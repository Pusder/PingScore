package com.comm.pingscore;

import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.InputType;
import android.view.View;
import android.view.Gravity;
import android.view.Window;
import android.widget.Button;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.drawable.ColorDrawable;
import android.widget.ListView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.fragment.app.Fragment;
import com.google.android.material.snackbar.Snackbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String PREFS = "match_state";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_MODE = "mode";
    private static final String KEY_PLAYERS = "players";
    private static final String KEY_HISTORY = "wheel_history";
    private static final String KEY_TEAM_PENDING = "team_pending";
    private static final String KEY_UNDO_PROMPT = "undo_prompt";
    private static final String KEY_SOUND = "sound_enabled";
    private static final String KEY_VIBRATION = "vibration_enabled";
    private static final String KEY_DEFAULT_MODE = "default_mode";

    private final List<String> wheelPlayers = new ArrayList<>();
    private final List<String> teamPending = new ArrayList<>();
    private final List<String> teamNames = new ArrayList<>();
    private final List<String> teamRosters = new ArrayList<>();
    private final List<Integer> teamWins = new ArrayList<>();
    private final List<SetTabAdapter.Item> setTabItems = new ArrayList<>();
    private MatchEngine engine;
    private MatchViewModel viewModel;
    private SharedPreferences prefs;
    private String mode = "regular";
    private String playerOne = "玩家 1";
    private String playerTwo = "玩家 2";
    private int targetScore = 11;
    private int serveInterval = 2;
    private String teamOne = "队伍 1";
    private String teamTwo = "队伍 2";
    private String teamOneMembers = "";
    private String teamTwoMembers = "";
    private String doubleOneMembers = "";
    private String doubleTwoMembers = "";
    private String doubleServer = "";
    private String doubleReceiver = "";
    private int teamWinsOne;
    private int teamWinsTwo;
    private int teamTargetWins = 2;
    private long pauseStartedAt;
    private int lastServer = -1;
    private boolean undoPrompt = true;
    private boolean soundEnabled = true;
    private boolean vibrationEnabled = true;
    private String defaultMode = "regular";
    private int defaultBestOf = 3;
    private final Handler pauseHandler = new Handler(Looper.getMainLooper());
    private final Runnable pauseTicker = new Runnable() {
        @Override
        public void run() {
            if (engine != null && engine.getPauseOwner() >= 0 && pauseStartedAt > 0) {
                render();
                pauseHandler.postDelayed(this, 1_000L);
            }
        }
    };
    private final Runnable pauseExpiry = () -> {
        if (engine != null && engine.getPauseOwner() >= 0 && pauseStartedAt > 0
                && System.currentTimeMillis() - pauseStartedAt >= 60_000L) {
            engine.resume();
            pauseStartedAt = 0L;
            pauseHandler.removeCallbacks(pauseTicker);
            persist();
            render();
            toast("暂停已满 1 分钟，比赛已恢复");
        }
    };

    private TextView startButton;
    private TextView progressView;
    private TextView playerOneName;
    private TextView playerTwoName;
    private TextView playerOneServe;
    private TextView playerTwoServe;
    private TextView playerOneScore;
    private TextView playerTwoScore;
    private TextView pauseButton;
    private TextView serveButton;
    private View settingsButton;
    private TextView pauseStatus;
    private TextView scoreAdjustButton;
    private View scoreboardRoot;
    private TextView teamQueueButton;
    private TextView modeInfoButton;
    private RecyclerView setTabsList;
    private int selectedGameIndex;

    private static final class TeamEditor {
        final LinearLayout card;
        final EditText nameInput;
        final LinearLayout memberList;
        final TextView removeButton;
        final List<EditText> memberInputs = new ArrayList<>();

        TeamEditor(LinearLayout card, EditText nameInput, LinearLayout memberList,
                   TextView removeButton) {
            this.card = card;
            this.nameInput = nameInput;
            this.memberList = memberList;
            this.removeButton = removeButton;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(Math.max(dp(16), systemBars.left), systemBars.top,
                    Math.max(dp(16), systemBars.right), systemBars.bottom);
            return insets;
        });

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        loadGlobalSettings();
        loadTeamSession();
        loadDoubleSession();
        viewModel = new ViewModelProvider(this).get(MatchViewModel.class);
        bindViews();
        if (viewModel.hasSession()) {
            engine = viewModel.getEngine();
            mode = viewModel.getMode();
            playerOne = viewModel.getPlayerOne();
            playerTwo = viewModel.getPlayerTwo();
            targetScore = engine.getTargetScore();
            serveInterval = engine.getServeInterval();
            if (engine.getPauseOwner() >= 0) {
                pauseStartedAt = prefs.getLong("pause_started_at", System.currentTimeMillis());
                schedulePauseExpiry();
            }
        } else {
            loadSavedMatch();
        }
        bindViews();
        Fragment scoreboard = getSupportFragmentManager()
                .findFragmentById(R.id.scoreboard_fragment_container);
        if (scoreboard == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.scoreboard_fragment_container, new ScoreboardFragment())
                    .commit();
        } else if (scoreboard.getView() != null) {
            onScoreboardReady(scoreboard.getView());
        }
    }

    private void bindViews() {
        startButton = findViewById(R.id.start_button);
        progressView = findViewById(R.id.match_progress);
        teamQueueButton = findViewById(R.id.team_queue_button);
        modeInfoButton = findViewById(R.id.mode_info_button);
        setTabsList = findViewById(R.id.set_tabs_list);
        int tabOrientation = getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                ? LinearLayoutManager.VERTICAL : LinearLayoutManager.HORIZONTAL;
        setTabsList.setLayoutManager(new LinearLayoutManager(this, tabOrientation, false));
    }

    void onScoreboardReady(View scoreboardView) {
        if (scoreboardRoot != null) return;
        scoreboardRoot = scoreboardView;
        playerOneName = scoreboardRoot.findViewById(R.id.player_one_name);
        playerTwoName = scoreboardRoot.findViewById(R.id.player_two_name);
        playerOneServe = scoreboardRoot.findViewById(R.id.player_one_serve);
        playerTwoServe = scoreboardRoot.findViewById(R.id.player_two_serve);
        playerOneScore = scoreboardRoot.findViewById(R.id.player_one_score);
        playerTwoScore = scoreboardRoot.findViewById(R.id.player_two_score);
        View landSettingsButton = findViewById(R.id.land_settings_button);
        View landPauseButton = findViewById(R.id.land_pause_button);
        View landServeButton = findViewById(R.id.land_serve_button);
        settingsButton = landSettingsButton != null
                ? landSettingsButton : scoreboardRoot.findViewById(R.id.settings_button);
        pauseButton = landPauseButton != null
                ? (TextView) landPauseButton : scoreboardRoot.findViewById(R.id.pause_button);
        serveButton = landServeButton != null
                ? (TextView) landServeButton : scoreboardRoot.findViewById(R.id.serve_button);
        pauseStatus = scoreboardRoot.findViewById(R.id.pause_status);
        scoreAdjustButton = scoreboardRoot.findViewById(R.id.score_adjust_button);
        bindActions();
        render();
    }

    private void bindActions() {
        startButton.setOnClickListener(v -> {
            if (engine != null && engine.isStarted() && !engine.isFinished()) {
                if (engine.getPauseOwner() >= 0) {
                    engine.resume();
                    pauseStartedAt = 0L;
                    pauseHandler.removeCallbacks(pauseExpiry);
                    pauseHandler.removeCallbacks(pauseTicker);
                    persist();
                    render();
                } else {
                    toast("比赛进行中");
                }
            } else {
                showModernMatchSetup();
            }
        });
        settingsButton.setOnClickListener(v -> {
            if (engine != null && engine.getPauseOwner() >= 0) {
                toast("暂停锁定中，暂不能修改本场设定");
            } else {
                showModernMatchSetup();
            }
        });
        scoreboardRoot.findViewById(R.id.player_one_plus).setOnClickListener(v -> changeScore(0, true));
        scoreboardRoot.findViewById(R.id.player_one_minus).setOnClickListener(v -> changeScore(0, false));
        scoreboardRoot.findViewById(R.id.player_two_plus).setOnClickListener(v -> changeScore(1, true));
        scoreboardRoot.findViewById(R.id.player_two_minus).setOnClickListener(v -> changeScore(1, false));
        pauseButton.setOnClickListener(v -> {
            if (engine != null && engine.getPauseOwner() >= 0) {
                showPauseLockedDialog();
            } else {
                showModernPauseChooser();
            }
        });
        serveButton.setOnClickListener(v -> showServeChooser());
        scoreAdjustButton.setOnClickListener(v -> showScoreAdjustDialog());
        findViewById(R.id.history_button).setOnClickListener(v -> showHistory());
        findViewById(R.id.global_settings_button).setOnClickListener(v -> showGlobalSettings());
        teamQueueButton.setOnClickListener(v -> showAddTeamMatchDialog());
        modeInfoButton.setOnClickListener(v -> showModeInfo());
    }

    private void changeScore(int player, boolean add) {
        if (engine == null || !engine.isStarted()) {
            toast("请先开始比赛");
            return;
        }
        try {
            int completedBefore = engine.getGameRecords().size();
            if (add) {
                engine.addPoint(player);
            } else {
                engine.subtractPoint(player);
            }
            playFeedback();
            if (mode.equals("doubles") && add) {
                rotateDoublesIfNeeded();
            }
            if (engine.getGameRecords().size() != completedBefore) {
                selectedGameIndex = engine.isFinished()
                        ? Math.max(0, engine.getGameRecords().size() - 1)
                        : engine.getGameRecords().size();
            }
            persist();
            render();
            showUndoSnackbar(player, add);
            if (engine.isFinished()) {
                if (mode.equals("team")) finishTeamMatch();
                else finishMatch();
            }
        } catch (IllegalStateException e) {
            toast(engine.getPauseOwner() >= 0 ? "暂停锁定中，请先结束暂停" : "比赛尚未开始");
        }
    }

    private void render() {
        if (engine == null) {
            engine = new MatchEngine(3, MatchEngine.REGULAR_TARGET, 2);
        }
        if (mode.equals("doubles")) ensureDoublesRotation();
        playerOneName.setText(playerOne);
        playerTwoName.setText(playerTwo);
        if (mode.equals("doubles")) {
            playerOneName.setText(teamOne + "\n" + memberSummary(doubleOneMembers));
            playerTwoName.setText(teamTwo + "\n" + memberSummary(doubleTwoMembers));
        }
        int currentGameIndex = engine.getGameRecords().size();
        if (engine.isFinished()) {
            selectedGameIndex = Math.min(selectedGameIndex,
                    Math.max(0, engine.getGameRecords().size() - 1));
        } else {
            selectedGameIndex = Math.max(0, Math.min(selectedGameIndex, engine.getBestOf() - 1));
        }
        int displayedOne = engine.getCurrentOne();
        int displayedTwo = engine.getCurrentTwo();
        if (selectedGameIndex < engine.getGameRecords().size()) {
            MatchEngine.GameRecord record = engine.getGameRecords().get(selectedGameIndex);
            displayedOne = record.playerOne;
            displayedTwo = record.playerTwo;
        } else if (selectedGameIndex != currentGameIndex) {
            displayedOne = 0;
            displayedTwo = 0;
        }
        playerOneScore.setText(String.valueOf(displayedOne));
        playerTwoScore.setText(String.valueOf(displayedTwo));
        renderSetTabs();
        int displayGame = Math.min(engine.getGameNumber(), engine.getBestOf());
        if (mode.equals("team")) {
            progressView.setText("团队赛 · " + teamStandings()
                    + " · 当前小场 " + playerOne + " vs " + playerTwo);
        } else {
            String modeTitle = mode.equals("wheel") ? "车轮赛 · "
                    : mode.equals("entertainment") ? "娱乐赛 · "
                    : mode.equals("doubles") ? "双打 · " : "正规赛 · ";
            progressView.setText(modeTitle
                    + "BO" + engine.getBestOf() + " · 第 " + (selectedGameIndex + 1) + " 局 / " + engine.getBestOf()
                    + " · 大比分 " + engine.getWinsOne() + ":" + engine.getWinsTwo());
        }
        boolean started = engine.isStarted() && !engine.isFinished();
        startButton.setText(started ? (engine.getPauseOwner() >= 0 ? "继续比赛" : "比赛中")
                : (prefs.getBoolean(KEY_ACTIVE, false) ? "继续比赛" : "开始比赛"));
        boolean selectedCurrent = selectedGameIndex == currentGameIndex && !engine.isFinished();
        boolean playerOneServing = selectedCurrent && engine.getCurrentServer() == 0;
        lastServer = engine.getCurrentServer();
        if (mode.equals("doubles")) {
            playerOneServe.setText(selectedCurrent
                    ? (playerOneServing ? "● 发球：" + safeName(doubleServer) : "接发：" + safeName(doubleReceiver))
                    : "本局记录");
            playerTwoServe.setText(selectedCurrent
                    ? (playerOneServing ? "接发：" + safeName(doubleReceiver) : "● 发球：" + safeName(doubleServer))
                    : "本局记录");
        } else {
            playerOneServe.setText(selectedCurrent ? (playerOneServing ? "● 当前发球" : "接发") : "本局记录");
            playerTwoServe.setText(selectedCurrent ? (playerOneServing ? "接发" : "● 当前发球") : "本局记录");
        }
        playerOneServe.setBackgroundResource(playerOneServing
                ? R.drawable.bg_serve_active : R.drawable.bg_set_tab);
        playerTwoServe.setBackgroundResource(playerOneServing
                ? R.drawable.bg_set_tab : R.drawable.bg_serve_active);
        playerOneServe.setTextColor(getColor(playerOneServing
                ? R.color.score_green_dark : R.color.score_muted));
        playerTwoServe.setTextColor(getColor(playerOneServing
                ? R.color.score_muted : R.color.score_green_dark));
        pauseButton.setText(engine.getPauseOwner() >= 0 ? "结束暂停" : "暂停");
        pauseButton.setEnabled(started || engine.getPauseOwner() >= 0);
        if (engine.getPauseOwner() >= 0) {
            pauseButton.setText("暂停中 " + formatPauseRemaining());
        }
        if (pauseStatus != null) {
            boolean paused = engine.getPauseOwner() >= 0;
            pauseStatus.setVisibility(paused ? View.VISIBLE : View.GONE);
            if (paused) {
                pauseStatus.setText("暂停锁定  ·  " + pauseOwnerName()
                        + "  ·  " + formatPauseRemaining());
            }
        }
        settingsButton.setEnabled(engine.getPauseOwner() < 0);
        findViewById(R.id.global_settings_button)
                .setEnabled(engine.getPauseOwner() < 0);
        serveButton.setEnabled(started && selectedCurrent && engine.getPauseOwner() < 0);
        setScoreControlsEnabled(started && selectedCurrent && engine.getPauseOwner() < 0,
                selectedCurrent);
        boolean completedSelected = selectedGameIndex < engine.getGameRecords().size();
        scoreAdjustButton.setVisibility(completedSelected ? View.VISIBLE : View.GONE);
        scoreAdjustButton.setText("赛果调整");
        teamQueueButton.setVisibility(mode.equals("team") ? View.VISIBLE : View.GONE);
        teamQueueButton.setText("待赛  + 添加对局（" + teamPending.size() + "）");
        modeInfoButton.setVisibility(mode.equals("doubles") || mode.equals("team")
                ? View.VISIBLE : View.GONE);
        modeInfoButton.setText(mode.equals("doubles") ? "双打规则说明" : "团队赛规则说明");
    }

    private void setScoreControlsEnabled(boolean enabled, boolean showControls) {
        scoreboardRoot.findViewById(R.id.player_one_plus).setEnabled(enabled);
        scoreboardRoot.findViewById(R.id.player_one_minus).setEnabled(enabled);
        scoreboardRoot.findViewById(R.id.player_two_plus).setEnabled(enabled);
        scoreboardRoot.findViewById(R.id.player_two_minus).setEnabled(enabled);
        scoreboardRoot.findViewById(R.id.player_one_plus).setAlpha(enabled ? 1f : 0.48f);
        scoreboardRoot.findViewById(R.id.player_one_minus).setAlpha(enabled ? 1f : 0.48f);
        scoreboardRoot.findViewById(R.id.player_two_plus).setAlpha(enabled ? 1f : 0.48f);
        scoreboardRoot.findViewById(R.id.player_two_minus).setAlpha(enabled ? 1f : 0.48f);
        int visibility = showControls ? View.VISIBLE : View.GONE;
        scoreboardRoot.findViewById(R.id.player_one_plus).setVisibility(visibility);
        scoreboardRoot.findViewById(R.id.player_one_minus).setVisibility(visibility);
        scoreboardRoot.findViewById(R.id.player_two_plus).setVisibility(visibility);
        scoreboardRoot.findViewById(R.id.player_two_minus).setVisibility(visibility);
    }

    private void renderSetTabs() {
        setTabItems.clear();
        int completed = engine.getGameRecords().size();
        int current = engine.isFinished() ? -1 : completed;
        for (int index = 0; index < engine.getBestOf(); index++) {
            if (index < completed) {
                MatchEngine.GameRecord record = engine.getGameRecords().get(index);
                setTabItems.add(new SetTabAdapter.Item(index + 1, SetTabAdapter.COMPLETED,
                        record.playerOne + ":" + record.playerTwo, selectedGameIndex == index));
            } else if (index == current) {
                setTabItems.add(new SetTabAdapter.Item(index + 1, SetTabAdapter.CURRENT,
                        engine.getCurrentOne() + ":" + engine.getCurrentTwo(), selectedGameIndex == index));
            } else {
                setTabItems.add(new SetTabAdapter.Item(index + 1, SetTabAdapter.PENDING,
                        "待赛", selectedGameIndex == index));
            }
        }
        setTabsList.setAdapter(new SetTabAdapter(setTabItems, position -> {
            selectedGameIndex = position;
            render();
        }));
    }

    private void showLegacyMatchSetup() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        content.setPadding(pad, dp(8), pad, 0);

        RadioGroup modeGroup = new RadioGroup(this);
        RadioButton regular = new RadioButton(this);
        regular.setText("正规赛");
        regular.setId(View.generateViewId());
        RadioButton wheel = new RadioButton(this);
        wheel.setText("车轮赛");
        wheel.setId(View.generateViewId());
        RadioButton team = new RadioButton(this);
        team.setText("团队赛");
        team.setId(View.generateViewId());
        modeGroup.addView(regular);
        modeGroup.addView(wheel);
        modeGroup.addView(team);
        regular.setChecked(mode.equals("regular"));
        wheel.setChecked(mode.equals("wheel"));
        team.setChecked(mode.equals("team"));
        content.addView(modeGroup);

        Spinner boSpinner = new Spinner(this);
        content.addView(boSpinner);
        EditText one = new EditText(this);
        one.setHint("玩家 1 名称");
        one.setText(playerOne.equals("玩家 1") ? "" : playerOne);
        content.addView(one);
        EditText two = new EditText(this);
        two.setHint("玩家 2 名称");
        two.setText(playerTwo.equals("玩家 2") ? "" : playerTwo);
        content.addView(two);
        EditText pool = new EditText(this);
        pool.setHint("车轮赛选手，每行一人，至少 2 人");
        pool.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        pool.setMinLines(3);
        pool.setVisibility(mode.equals("wheel") ? View.VISIBLE : View.GONE);
        content.addView(pool);
        one.setVisibility(mode.equals("regular") ? View.VISIBLE : View.GONE);
        two.setVisibility(mode.equals("regular") ? View.VISIBLE : View.GONE);
        EditText teamOneInput = new EditText(this);
        teamOneInput.setHint("队伍 1 名称");
        teamOneInput.setText(teamOne.equals("队伍 1") ? "" : teamOne);
        content.addView(teamOneInput);
        EditText teamTwoInput = new EditText(this);
        teamTwoInput.setHint("队伍 2 名称");
        teamTwoInput.setText(teamTwo.equals("队伍 2") ? "" : teamTwo);
        content.addView(teamTwoInput);
        EditText teamOneMembersInput = new EditText(this);
        teamOneMembersInput.setHint("队伍 1 队员，每行一人，至少 2 人");
        teamOneMembersInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        teamOneMembersInput.setMinLines(2);
        teamOneMembersInput.setText(teamOneMembers);
        content.addView(teamOneMembersInput);
        EditText teamTwoMembersInput = new EditText(this);
        teamTwoMembersInput.setHint("队伍 2 队员，每行一人，至少 2 人");
        teamTwoMembersInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        teamTwoMembersInput.setMinLines(2);
        teamTwoMembersInput.setText(teamTwoMembers);
        content.addView(teamTwoMembersInput);
        teamOneInput.setVisibility(mode.equals("team") ? View.VISIBLE : View.GONE);
        teamTwoInput.setVisibility(mode.equals("team") ? View.VISIBLE : View.GONE);
        teamOneMembersInput.setVisibility(mode.equals("team") ? View.VISIBLE : View.GONE);
        teamTwoMembersInput.setVisibility(mode.equals("team") ? View.VISIBLE : View.GONE);

        ArrayAdapter<String> boAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, new String[]{"BO3", "BO5", "BO7"});
        boSpinner.setAdapter(boAdapter);
        modeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isWheel = checkedId == wheel.getId();
            boolean isTeam = checkedId == team.getId();
            pool.setVisibility(isWheel ? View.VISIBLE : View.GONE);
            one.setVisibility(!isWheel && !isTeam ? View.VISIBLE : View.GONE);
            two.setVisibility(!isWheel && !isTeam ? View.VISIBLE : View.GONE);
            teamOneInput.setVisibility(isTeam ? View.VISIBLE : View.GONE);
            teamTwoInput.setVisibility(isTeam ? View.VISIBLE : View.GONE);
            teamOneMembersInput.setVisibility(isTeam ? View.VISIBLE : View.GONE);
            teamTwoMembersInput.setVisibility(isTeam ? View.VISIBLE : View.GONE);
            if (isWheel) {
                boSpinner.setAdapter(new ArrayAdapter<>(this,
                        android.R.layout.simple_spinner_dropdown_item, new String[]{"BO1"}));
            } else {
                boSpinner.setAdapter(new ArrayAdapter<>(this,
                        android.R.layout.simple_spinner_dropdown_item, new String[]{"BO3", "BO5", "BO7"}));
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("新建比赛")
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("开始", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            boolean isWheel = wheel.isChecked();
            boolean isTeam = team.isChecked();
            if (isWheel) {
                wheelPlayers.clear();
                for (String name : pool.getText().toString().split("\\r?\\n")) {
                    if (!name.trim().isEmpty() && !wheelPlayers.contains(name.trim())) {
                        wheelPlayers.add(name.trim());
                    }
                }
                if (wheelPlayers.size() < 2) {
                    pool.setError("至少添加 2 名选手");
                    return;
                }
                mode = "wheel";
                persistWheelPlayers();
                dialog.dismiss();
                showWheelPairChooser();
                return;
            }
            if (isTeam) {
                teamOne = teamOneInput.getText().toString().trim().isEmpty()
                        ? "队伍 1" : teamOneInput.getText().toString().trim();
                teamTwo = teamTwoInput.getText().toString().trim().isEmpty()
                        ? "队伍 2" : teamTwoInput.getText().toString().trim();
                teamOneMembers = normalizeMembers(teamOneMembersInput.getText().toString());
                teamTwoMembers = normalizeMembers(teamTwoMembersInput.getText().toString());
                if (teamOneMembers.split("\\n").length < 2 || teamTwoMembers.split("\\n").length < 2) {
                    teamOneMembersInput.setError("双方至少各添加 2 名队员");
                    return;
                }
                mode = "team";
                teamWinsOne = 0;
                teamWinsTwo = 0;
                teamPending.clear();
                prefs.edit().putString(KEY_MODE, mode).apply();
                persistTeamSession();
                dialog.dismiss();
                render();
                showAddTeamMatchDialog();
                return;
            }
            mode = "regular";
            playerOne = one.getText().toString().trim().isEmpty() ? "玩家 1" : one.getText().toString().trim();
            playerTwo = two.getText().toString().trim().isEmpty() ? "玩家 2" : two.getText().toString().trim();
            int bestOf = Integer.parseInt(String.valueOf(boSpinner.getSelectedItem()).substring(2));
            engine = new MatchEngine(bestOf, MatchEngine.REGULAR_TARGET);
            engine.start();
            persist();
            dialog.dismiss();
            render();
        }));
        showStyledDialog(dialog);
    }

    private TextView createEditorAction(String text) {
        TextView action = new TextView(this);
        action.setText(text);
        action.setGravity(Gravity.CENTER);
        action.setTextColor(getColor(R.color.score_blue));
        action.setTextSize(14);
        action.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        action.setPadding(dp(12), 0, dp(12), 0);
        action.setBackgroundResource(R.drawable.bg_set_current);
        action.setClickable(true);
        action.setFocusable(true);
        return action;
    }

    private LinearLayout createEditorSection(String title) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(12), dp(10), dp(12), dp(10));
        section.setBackgroundResource(R.drawable.bg_dialog_section);
        TextView label = new TextView(this);
        label.setText(title);
        label.setTextColor(getColor(R.color.score_ink));
        label.setTextSize(15);
        label.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        section.addView(label, new LinearLayout.LayoutParams(-1, dp(30)));
        return section;
    }

    private EditText addEditableNameRow(LinearLayout list, List<EditText> inputs,
                                       String value, String hint) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(2), dp(6), dp(2));
        row.setBackgroundResource(R.drawable.bg_input_item);

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setText(value == null ? "" : value);
        input.setTextSize(16);
        input.setTextColor(getColor(R.color.score_ink));
        input.setHintTextColor(getColor(R.color.score_muted));
        input.setBackground(null);
        input.setPadding(0, 0, 0, 0);
        row.addView(input, new LinearLayout.LayoutParams(0, dp(48), 1));

        TextView remove = new TextView(this);
        remove.setText("移除");
        remove.setGravity(Gravity.CENTER);
        remove.setTextColor(getColor(R.color.score_muted));
        remove.setTextSize(13);
        remove.setPadding(dp(8), 0, dp(8), 0);
        remove.setOnClickListener(v -> {
            inputs.remove(input);
            list.removeView(row);
        });
        row.addView(remove, new LinearLayout.LayoutParams(dp(56), dp(48)));
        inputs.add(input);
        list.addView(row, new LinearLayout.LayoutParams(-1, dp(56)));
        return input;
    }

    private TeamEditor addTeamEditor(LinearLayout host, String name, String encodedMembers) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(8), dp(10), dp(10));
        card.setBackgroundResource(R.drawable.bg_input_item);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        EditText nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setHint("队伍名称");
        nameInput.setText(name == null ? "" : name);
        nameInput.setTextSize(16);
        nameInput.setTextColor(getColor(R.color.score_ink));
        nameInput.setHintTextColor(getColor(R.color.score_muted));
        nameInput.setBackground(null);
        nameInput.setPadding(0, 0, 0, 0);
        header.addView(nameInput, new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView removeTeam = createEditorAction("移除队伍");
        removeTeam.setTextColor(getColor(R.color.score_muted));
        removeTeam.setBackgroundResource(R.drawable.bg_set_tab);
        removeTeam.setVisibility(View.GONE);
        header.addView(removeTeam, new LinearLayout.LayoutParams(dp(88), dp(42)));
        card.addView(header, new LinearLayout.LayoutParams(-1, dp(48)));

        LinearLayout memberList = new LinearLayout(this);
        memberList.setOrientation(LinearLayout.VERTICAL);
        card.addView(memberList, new LinearLayout.LayoutParams(-1, -2));
        TeamEditor editor = new TeamEditor(card, nameInput, memberList, removeTeam);
        TextView addMember = createEditorAction("＋ 添加选手");
        addMember.setOnClickListener(v -> addEditableNameRow(memberList,
                editor.memberInputs, "", "队员姓名"));
        card.addView(addMember, new LinearLayout.LayoutParams(-1, dp(42)));

        List<String> members = memberList(encodedMembers);
        if (members.isEmpty()) {
            addEditableNameRow(memberList, editor.memberInputs, "", "队员姓名");
            addEditableNameRow(memberList, editor.memberInputs, "", "队员姓名");
        } else {
            for (String member : members) {
                addEditableNameRow(memberList, editor.memberInputs, member, "队员姓名");
            }
        }
        host.addView(card, new LinearLayout.LayoutParams(-1, -2));
        return editor;
    }

    private String readEditorNames(List<EditText> inputs) {
        List<String> values = new ArrayList<>();
        for (EditText input : inputs) {
            String value = input.getText().toString().trim();
            if (!value.isEmpty() && !values.contains(value)) values.add(value);
        }
        return String.join("\n", values);
    }

    private String readTeamEditorMembers(TeamEditor editor) {
        return normalizeMembers(readEditorNames(editor.memberInputs));
    }

    private void updateModernSetupFields(int checkedId, RadioButton entertainment,
                                         RadioButton wheel, RadioButton doubles,
                                         RadioButton team, EditText one, EditText two,
                                         LinearLayout wheelSection, LinearLayout doublesSection,
                                         LinearLayout teamSection, Spinner score,
                                         Spinner serve, Spinner bo) {
        boolean isEntertainment = checkedId == entertainment.getId();
        boolean isWheel = checkedId == wheel.getId();
        boolean isDoubles = checkedId == doubles.getId();
        boolean isTeam = checkedId == team.getId();
        boolean isSingle = !isWheel && !isDoubles && !isTeam;
        one.setVisibility(isSingle ? View.VISIBLE : View.GONE);
        two.setVisibility(isSingle ? View.VISIBLE : View.GONE);
        wheelSection.setVisibility(isWheel ? View.VISIBLE : View.GONE);
        doublesSection.setVisibility(isDoubles ? View.VISIBLE : View.GONE);
        teamSection.setVisibility(isTeam ? View.VISIBLE : View.GONE);
        score.setVisibility(isEntertainment ? View.VISIBLE : View.GONE);
        serve.setVisibility(isEntertainment ? View.VISIBLE : View.GONE);
        if (isWheel) setSpinnerValues(bo, new String[]{"BO1"}, "BO1");
        else setBoOptions(bo, isEntertainment ? "entertainment" : "regular",
                preferredSetupBestOf());
    }

    private void showModernMatchSetup() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(8), dp(18), dp(12));

        TextView rules = new TextView(this);
        rules.setText("正规赛：11 分，每 2 分换发，10:10 后每 1 分换发。\n"
                + "娱乐赛：BO1/BO3/BO5/BO7、11/21 分、每 1/2/5 分换发均可选。\n"
                + "BO3 表示三局两胜，BO5、BO7 以此类推。");
        rules.setTextColor(getColor(R.color.score_muted));
        rules.setTextSize(13);
        rules.setLineSpacing(dp(3), 1f);
        content.addView(rules, new LinearLayout.LayoutParams(-1, -2));

        RadioGroup modeGroup = new RadioGroup(this);
        RadioButton regular = createModeButton("正规赛");
        RadioButton entertainment = createModeButton("娱乐赛");
        RadioButton wheel = createModeButton("车轮赛");
        RadioButton doubles = createModeButton("双打");
        RadioButton team = createModeButton("团队赛");
        modeGroup.addView(regular);
        modeGroup.addView(entertainment);
        modeGroup.addView(wheel);
        modeGroup.addView(doubles);
        modeGroup.addView(team);
        content.addView(modeGroup);

        Spinner boSpinner = new Spinner(this);
        Spinner scoreSpinner = new Spinner(this);
        Spinner serveSpinner = new Spinner(this);
        content.addView(boSpinner, new LinearLayout.LayoutParams(-1, dp(48)));
        content.addView(scoreSpinner, new LinearLayout.LayoutParams(-1, dp(48)));
        content.addView(serveSpinner, new LinearLayout.LayoutParams(-1, dp(48)));

        EditText one = new EditText(this);
        one.setHint("玩家 1 名称");
        one.setSingleLine(true);
        one.setText(playerOne.equals("玩家 1") ? "" : playerOne);
        content.addView(one, new LinearLayout.LayoutParams(-1, dp(52)));
        EditText two = new EditText(this);
        two.setHint("玩家 2 名称");
        two.setSingleLine(true);
        two.setText(playerTwo.equals("玩家 2") ? "" : playerTwo);
        content.addView(two, new LinearLayout.LayoutParams(-1, dp(52)));

        LinearLayout wheelSection = createEditorSection("车轮赛选手");
        LinearLayout wheelList = new LinearLayout(this);
        wheelList.setOrientation(LinearLayout.VERTICAL);
        wheelSection.addView(wheelList, new LinearLayout.LayoutParams(-1, -2));
        List<EditText> wheelInputs = new ArrayList<>();
        if (wheelPlayers.isEmpty()) {
            addEditableNameRow(wheelList, wheelInputs, "", "选手姓名");
            addEditableNameRow(wheelList, wheelInputs, "", "选手姓名");
        } else {
            for (String player : wheelPlayers) {
                addEditableNameRow(wheelList, wheelInputs, player, "选手姓名");
            }
        }
        TextView addWheelPlayer = createEditorAction("＋ 添加选手");
        addWheelPlayer.setOnClickListener(v -> addEditableNameRow(wheelList,
                wheelInputs, "", "选手姓名"));
        wheelSection.addView(addWheelPlayer, new LinearLayout.LayoutParams(-1, dp(44)));
        content.addView(wheelSection, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout doublesSection = createEditorSection("双打队伍与队员");
        List<TeamEditor> doubleEditors = new ArrayList<>();
        TeamEditor doubleOneEditor = addTeamEditor(doublesSection,
                teamOne.equals("队伍 1") ? "A 队" : teamOne, doubleOneMembers);
        TeamEditor doubleTwoEditor = addTeamEditor(doublesSection,
                teamTwo.equals("队伍 2") ? "B 队" : teamTwo, doubleTwoMembers);
        doubleEditors.add(doubleOneEditor);
        doubleEditors.add(doubleTwoEditor);
        content.addView(doublesSection, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout teamSection = createEditorSection("团队赛队伍与队员");
        LinearLayout teamList = new LinearLayout(this);
        teamList.setOrientation(LinearLayout.VERTICAL);
        teamSection.addView(teamList, new LinearLayout.LayoutParams(-1, -2));
        List<TeamEditor> teamEditors = new ArrayList<>();
        if (teamNames.size() >= 2) {
            for (int i = 0; i < teamNames.size(); i++) {
                teamEditors.add(addTeamEditor(teamList, teamNames.get(i), teamRosters.get(i)));
            }
        } else {
            teamEditors.add(addTeamEditor(teamList, teamOne, teamOneMembers));
            teamEditors.add(addTeamEditor(teamList, teamTwo, teamTwoMembers));
        }
        TextView addTeam = createEditorAction("＋ 添加队伍");
        addTeam.setOnClickListener(v -> {
            TeamEditor editor = addTeamEditor(teamList,
                    "队伍 " + (teamEditors.size() + 1), "");
            teamEditors.add(editor);
            editor.removeButton.setVisibility(View.VISIBLE);
            editor.removeButton.setOnClickListener(remove -> {
                teamEditors.remove(editor);
                teamList.removeView(editor.card);
            });
        });
        teamSection.addView(addTeam, new LinearLayout.LayoutParams(-1, dp(44)));
        Spinner teamTargetSpinner = new Spinner(this);
        setSpinnerValues(teamTargetSpinner,
                new String[]{"先胜 1 场", "先胜 2 场", "先胜 3 场"},
                "先胜 " + teamTargetWins + " 场");
        teamSection.addView(teamTargetSpinner, new LinearLayout.LayoutParams(-1, dp(48)));
        content.addView(teamSection, new LinearLayout.LayoutParams(-1, -2));

        setBoOptions(boSpinner, selectedSetupMode(), preferredSetupBestOf());
        setSpinnerValues(scoreSpinner, new String[]{"11 分", "21 分"},
                targetScore == 21 ? "21 分" : "11 分");
        setSpinnerValues(serveSpinner, new String[]{"每 1 分换发", "每 2 分换发", "每 5 分换发"},
                serveInterval == 1 ? "每 1 分换发" : serveInterval == 5 ? "每 5 分换发" : "每 2 分换发");

        String selectedMode = engine != null && engine.isStarted() ? mode : defaultMode;
        RadioButton selectedButton = selectedMode.equals("entertainment") ? entertainment
                : selectedMode.equals("wheel") ? wheel
                : selectedMode.equals("doubles") ? doubles
                : selectedMode.equals("team") ? team : regular;
        modeGroup.setOnCheckedChangeListener((group, checkedId) ->
                updateModernSetupFields(checkedId, entertainment, wheel, doubles, team,
                        one, two, wheelSection, doublesSection, teamSection,
                        scoreSpinner, serveSpinner, boSpinner));
        modeGroup.check(selectedButton.getId());

        ScrollView setupScroll = new ScrollView(this);
        setupScroll.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("本场设定")
                .setView(setupScroll)
                .setNegativeButton("取消", null)
                .setNeutralButton("重置当前", null)
                .setPositiveButton("开始 / 应用", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                AlertDialog resetDialog = new AlertDialog.Builder(this)
                        .setTitle("重置当前比赛？")
                        .setMessage("当前比分、局分和暂停状态会清空，比赛配置会保留。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("确认重置", (d, w) -> {
                            clearActive();
                            dialog.dismiss();
                        }).create();
                showStyledDialog(resetDialog);
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                boolean isEntertainment = entertainment.isChecked();
                boolean isWheel = wheel.isChecked();
                boolean isDoubles = doubles.isChecked();
                boolean isTeam = team.isChecked();
                if (isWheel) {
                    wheelPlayers.clear();
                    String names = readEditorNames(wheelInputs);
                    wheelPlayers.addAll(Arrays.asList(names.split("\\n")));
                    if (wheelPlayers.size() < 2) {
                        toast("车轮赛至少需要 2 名选手");
                        return;
                    }
                    mode = "wheel";
                    targetScore = 11;
                    serveInterval = 2;
                    persistWheelPlayers();
                    engine = new MatchEngine(1, targetScore, serveInterval);
                    dialog.dismiss();
                    showWheelPairChooser();
                    return;
                }
                int bestOf = parseBestOf(boSpinner);
                if (isTeam) {
                    if (teamEditors.size() < 2) {
                        toast("至少需要 2 支队伍");
                        return;
                    }
                    teamNames.clear();
                    teamRosters.clear();
                    for (TeamEditor editor : teamEditors) {
                        String name = editor.nameInput.getText().toString().trim();
                        String roster = readTeamEditorMembers(editor);
                        int count = memberList(roster).size();
                        if (name.isEmpty() || count < 2 || count > 6) {
                            toast("每支队伍需要名称和 2-6 名队员");
                            return;
                        }
                        if (teamNames.contains(name)) {
                            toast("队伍名称不能重复");
                            return;
                        }
                        teamNames.add(name);
                        teamRosters.add(roster);
                    }
                    teamOne = teamNames.get(0);
                    teamTwo = teamNames.get(1);
                    teamOneMembers = teamRosters.get(0);
                    teamTwoMembers = teamRosters.get(1);
                    teamTargetWins = Integer.parseInt(String.valueOf(teamTargetSpinner.getSelectedItem())
                            .replaceAll("[^0-9]", ""));
                    mode = "team";
                    teamWinsOne = 0;
                    teamWinsTwo = 0;
                    teamWins.clear();
                    for (int i = 0; i < teamNames.size(); i++) teamWins.add(0);
                    teamPending.clear();
                    engine = new MatchEngine(3, MatchEngine.REGULAR_TARGET, 2);
                    persistTeamSession();
                    persist();
                    dialog.dismiss();
                    render();
                    showAddTeamMatchDialog();
                    return;
                }
                if (isDoubles) {
                    String firstTeam = doubleOneEditor.nameInput.getText().toString().trim();
                    String secondTeam = doubleTwoEditor.nameInput.getText().toString().trim();
                    String firstMembers = readTeamEditorMembers(doubleOneEditor);
                    String secondMembers = readTeamEditorMembers(doubleTwoEditor);
                    if (firstTeam.isEmpty() || secondTeam.isEmpty()
                            || memberList(firstMembers).size() != 2
                            || memberList(secondMembers).size() != 2) {
                        toast("双打双方都需要名称和正好 2 名队员");
                        return;
                    }
                    teamOne = firstTeam;
                    teamTwo = secondTeam;
                    doubleOneMembers = firstMembers;
                    doubleTwoMembers = secondMembers;
                    mode = "doubles";
                    playerOne = teamOne;
                    playerTwo = teamTwo;
                    targetScore = 11;
                    serveInterval = 2;
                    doubleServer = memberList(doubleOneMembers).get(0);
                    doubleReceiver = memberList(doubleTwoMembers).get(0);
                    engine = new MatchEngine(bestOf, targetScore, serveInterval);
                    engine.start();
                    persistDoubleSession();
                    persist();
                    dialog.dismiss();
                    render();
                    return;
                }
                mode = isEntertainment ? "entertainment" : "regular";
                playerOne = valueOrDefault(one, "玩家 1");
                playerTwo = valueOrDefault(two, "玩家 2");
                targetScore = isEntertainment
                        ? Integer.parseInt(String.valueOf(scoreSpinner.getSelectedItem())
                        .replaceAll("[^0-9]", "")) : 11;
                serveInterval = isEntertainment ? parseServeInterval(serveSpinner) : 2;
                engine = new MatchEngine(bestOf, targetScore, serveInterval);
                engine.start();
                persist();
                dialog.dismiss();
                render();
            });
        });
        showStyledDialog(dialog);
    }

    private String selectedSetupMode() {
        return mode.equals("entertainment") ? "entertainment" : "regular";
    }

    private void showMatchSetup() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        content.setPadding(pad, dp(8), pad, 0);

        TextView rules = new TextView(this);
        rules.setText("正规赛：11 分制，每 2 分换发，10:10 后每 1 分换发。\n"
                + "娱乐赛：BO1/BO3/BO5/BO7，可选 11 或 21 分、每 1/2/5 分换发。\n"
                + "BO3 表示三局两胜，BO5、BO7 以此类推。");
        rules.setTextColor(getColor(R.color.score_muted));
        rules.setTextSize(13);
        rules.setPadding(0, 0, 0, dp(8));
        content.addView(rules);

        RadioGroup modeGroup = new RadioGroup(this);
        RadioButton regular = createModeButton("正规赛");
        RadioButton entertainment = createModeButton("娱乐赛");
        RadioButton wheel = createModeButton("车轮赛");
        RadioButton doubles = createModeButton("双打");
        RadioButton team = createModeButton("团队赛");
        modeGroup.addView(regular);
        modeGroup.addView(entertainment);
        modeGroup.addView(wheel);
        modeGroup.addView(doubles);
        modeGroup.addView(team);
        content.addView(modeGroup);

        Spinner boSpinner = new Spinner(this);
        Spinner scoreSpinner = new Spinner(this);
        Spinner serveSpinner = new Spinner(this);
        content.addView(boSpinner);
        content.addView(scoreSpinner);
        content.addView(serveSpinner);

        EditText one = new EditText(this);
        one.setHint("玩家 1 名称");
        one.setText(playerOne.equals("玩家 1") ? "" : playerOne);
        content.addView(one);
        EditText two = new EditText(this);
        two.setHint("玩家 2 名称");
        two.setText(playerTwo.equals("玩家 2") ? "" : playerTwo);
        content.addView(two);

        EditText pool = new EditText(this);
        pool.setHint("车轮赛选手，每行一人，至少 2 人");
        pool.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        pool.setMinLines(3);
        pool.setText(String.join("\n", wheelPlayers));
        content.addView(pool);

        EditText doubleOneInput = new EditText(this);
        doubleOneInput.setHint("双打 A 队队员，每行 2 人");
        doubleOneInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        doubleOneInput.setMinLines(2);
        doubleOneInput.setText(doubleOneMembers);
        content.addView(doubleOneInput);
        EditText doubleTwoInput = new EditText(this);
        doubleTwoInput.setHint("双打 B 队队员，每行 2 人");
        doubleTwoInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        doubleTwoInput.setMinLines(2);
        doubleTwoInput.setText(doubleTwoMembers);
        content.addView(doubleTwoInput);

        EditText teamOneInput = new EditText(this);
        teamOneInput.setHint("队伍 1 名称");
        teamOneInput.setText(teamOne.equals("队伍 1") ? "" : teamOne);
        content.addView(teamOneInput);
        EditText teamTwoInput = new EditText(this);
        teamTwoInput.setHint("队伍 2 名称");
        teamTwoInput.setText(teamTwo.equals("队伍 2") ? "" : teamTwo);
        content.addView(teamTwoInput);
        EditText teamOneMembersInput = new EditText(this);
        teamOneMembersInput.setHint("队伍 1 队员，每行 2-6 人");
        teamOneMembersInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        teamOneMembersInput.setMinLines(2);
        teamOneMembersInput.setText(teamOneMembers);
        content.addView(teamOneMembersInput);
        EditText teamTwoMembersInput = new EditText(this);
        teamTwoMembersInput.setHint("队伍 2 队员，每行 2-6 人");
        teamTwoMembersInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        teamTwoMembersInput.setMinLines(2);
        teamTwoMembersInput.setText(teamTwoMembers);
        content.addView(teamTwoMembersInput);
        EditText teamRosterInput = new EditText(this);
        teamRosterInput.setHint("多队模式：队名=队员1,队员2，每行一队（可选）");
        teamRosterInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        teamRosterInput.setMinLines(3);
        teamRosterInput.setText(encodeTeamRostersForInput());
        content.addView(teamRosterInput);
        Spinner teamTargetSpinner = new Spinner(this);
        content.addView(teamTargetSpinner);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(content);

        setBoOptions(boSpinner, mode, preferredSetupBestOf());
        setSpinnerValues(scoreSpinner, new String[]{"11 分", "21 分"},
                targetScore == 21 ? "21 分" : "11 分");
        setSpinnerValues(serveSpinner, new String[]{"每 1 分换发", "每 2 分换发", "每 5 分换发"},
                serveInterval == 1 ? "每 1 分换发" : serveInterval == 5 ? "每 5 分换发" : "每 2 分换发");
        setSpinnerValues(teamTargetSpinner, new String[]{"先胜 1 场", "先胜 2 场", "先胜 3 场"},
                "先胜 " + teamTargetWins + " 场");

        String selectedMode = engine != null && engine.isStarted() ? mode : defaultMode;
        regular.setChecked(selectedMode.equals("regular"));
        entertainment.setChecked(selectedMode.equals("entertainment"));
        wheel.setChecked(selectedMode.equals("wheel"));
        doubles.setChecked(selectedMode.equals("doubles"));
        team.setChecked(selectedMode.equals("team"));

        modeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isEntertainment = checkedId == entertainment.getId();
            boolean isWheel = checkedId == wheel.getId();
            boolean isDoubles = checkedId == doubles.getId();
            boolean isTeam = checkedId == team.getId();
            boolean isSingle = !isWheel && !isDoubles && !isTeam;
            one.setVisibility(isSingle ? View.VISIBLE : View.GONE);
            two.setVisibility(isSingle ? View.VISIBLE : View.GONE);
            pool.setVisibility(isWheel ? View.VISIBLE : View.GONE);
            doubleOneInput.setVisibility(isDoubles ? View.VISIBLE : View.GONE);
            doubleTwoInput.setVisibility(isDoubles ? View.VISIBLE : View.GONE);
            teamOneInput.setVisibility(isTeam || isDoubles ? View.VISIBLE : View.GONE);
            teamTwoInput.setVisibility(isTeam || isDoubles ? View.VISIBLE : View.GONE);
            teamOneMembersInput.setVisibility(isTeam ? View.VISIBLE : View.GONE);
            teamTwoMembersInput.setVisibility(isTeam ? View.VISIBLE : View.GONE);
            teamRosterInput.setVisibility(isTeam ? View.VISIBLE : View.GONE);
            teamTargetSpinner.setVisibility(isTeam ? View.VISIBLE : View.GONE);
            scoreSpinner.setVisibility(isEntertainment ? View.VISIBLE : View.GONE);
            serveSpinner.setVisibility(isEntertainment ? View.VISIBLE : View.GONE);
            if (isWheel) {
                setSpinnerValues(boSpinner, new String[]{"BO1"}, "BO1");
            } else if (isEntertainment) {
                setBoOptions(boSpinner, "entertainment", preferredSetupBestOf());
            } else {
                setBoOptions(boSpinner, "regular", preferredSetupBestOf());
            }
        });

        if (selectedMode.equals("entertainment")) entertainment.setChecked(true);
        else if (selectedMode.equals("wheel")) wheel.setChecked(true);
        else if (selectedMode.equals("doubles")) doubles.setChecked(true);
        else if (selectedMode.equals("team")) team.setChecked(true);
        else regular.setChecked(true);
        updateSetupFields(modeGroup, regular, entertainment, wheel, doubles, team, one, two,
                pool, doubleOneInput, doubleTwoInput, teamOneInput, teamTwoInput,
                teamOneMembersInput, teamTwoMembersInput, teamRosterInput, teamTargetSpinner,
                scoreSpinner, serveSpinner, boSpinner);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("本场设定")
                .setView(scrollView)
                .setNegativeButton("取消", null)
                .setNeutralButton("重置当前", null)
                .setPositiveButton("开始 / 应用", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                AlertDialog resetDialog = new AlertDialog.Builder(this)
                        .setTitle("重置当前比赛？")
                        .setMessage("当前比分、局分和暂停状态会清空，已保存的比赛配置保留。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("确认重置", (d, w) -> {
                            clearActive();
                            dialog.dismiss();
                        }).create();
                showStyledDialog(resetDialog);
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                boolean isEntertainment = entertainment.isChecked();
                boolean isWheel = wheel.isChecked();
                boolean isDoubles = doubles.isChecked();
                boolean isTeam = team.isChecked();
                if (isWheel) {
                    wheelPlayers.clear();
                    for (String name : pool.getText().toString().split("\\r?\\n")) {
                        String trimmed = name.trim();
                        if (!trimmed.isEmpty() && !wheelPlayers.contains(trimmed)) wheelPlayers.add(trimmed);
                    }
                    if (wheelPlayers.size() < 2) {
                        pool.setError("至少添加 2 名选手");
                        return;
                    }
                    mode = "wheel";
                    targetScore = 11;
                    serveInterval = 2;
                    persistWheelPlayers();
                    engine = new MatchEngine(1, targetScore, serveInterval);
                    dialog.dismiss();
                    showWheelPairChooser();
                    return;
                }
                int bestOf = parseBestOf(boSpinner);
                if (isTeam) {
                    teamOne = valueOrDefault(teamOneInput, "队伍 1");
                    teamTwo = valueOrDefault(teamTwoInput, "队伍 2");
                    teamOneMembers = normalizeMembers(teamOneMembersInput.getText().toString());
                    teamTwoMembers = normalizeMembers(teamTwoMembersInput.getText().toString());
                    List<String> parsedNames = new ArrayList<>();
                    List<String> parsedRosters = new ArrayList<>();
                    String rosterInput = teamRosterInput.getText().toString().trim();
                    if (!rosterInput.isEmpty()) {
                        parseTeamRosters(rosterInput, parsedNames, parsedRosters);
                    }
                    if (parsedNames.size() >= 2) {
                        teamNames.clear();
                        teamNames.addAll(parsedNames);
                        teamRosters.clear();
                        teamRosters.addAll(parsedRosters);
                        teamOne = teamNames.get(0);
                        teamTwo = teamNames.get(1);
                        teamOneMembers = teamRosters.get(0);
                        teamTwoMembers = teamRosters.get(1);
                    } else {
                        teamNames.clear();
                        teamNames.add(teamOne);
                        teamNames.add(teamTwo);
                        teamRosters.clear();
                        teamRosters.add(teamOneMembers);
                        teamRosters.add(teamTwoMembers);
                    }
                    if (teamNames.size() < 2 || !validTeamRosters(teamRosters)) {
                        teamRosterInput.setError("至少 2 支队伍，每队 2-6 名队员");
                        return;
                    }
                    teamTargetWins = Integer.parseInt(String.valueOf(teamTargetSpinner.getSelectedItem())
                            .replaceAll("[^0-9]", ""));
                    mode = "team";
                    teamWinsOne = 0;
                    teamWinsTwo = 0;
                    teamWins.clear();
                    for (int i = 0; i < teamNames.size(); i++) teamWins.add(0);
                    teamPending.clear();
                    engine = new MatchEngine(3, MatchEngine.REGULAR_TARGET, 2);
                    persistTeamSession();
                    persist();
                    dialog.dismiss();
                    render();
                    showAddTeamMatchDialog();
                    return;
                }
                if (isDoubles) {
                    teamOne = valueOrDefault(teamOneInput, "A 队");
                    teamTwo = valueOrDefault(teamTwoInput, "B 队");
                    doubleOneMembers = normalizeMembers(doubleOneInput.getText().toString());
                    doubleTwoMembers = normalizeMembers(doubleTwoInput.getText().toString());
                    if (memberList(doubleOneMembers).size() != 2 || memberList(doubleTwoMembers).size() != 2) {
                        doubleOneInput.setError("每队需要正好 2 名选手");
                        return;
                    }
                    mode = "doubles";
                    playerOne = teamOne;
                    playerTwo = teamTwo;
                    targetScore = 11;
                    serveInterval = 2;
                    doubleServer = memberList(doubleOneMembers).get(0);
                    doubleReceiver = memberList(doubleTwoMembers).get(0);
                    engine = new MatchEngine(bestOf, targetScore, serveInterval);
                    engine.start();
                    persistDoubleSession();
                    persist();
                    dialog.dismiss();
                    render();
                    return;
                }
                mode = isEntertainment ? "entertainment" : "regular";
                playerOne = valueOrDefault(one, "玩家 1");
                playerTwo = valueOrDefault(two, "玩家 2");
                targetScore = isEntertainment
                        ? Integer.parseInt(String.valueOf(scoreSpinner.getSelectedItem()).replaceAll("[^0-9]", ""))
                        : 11;
                serveInterval = isEntertainment ? parseServeInterval(serveSpinner) : 2;
                engine = new MatchEngine(bestOf, targetScore, serveInterval);
                engine.start();
                persist();
                dialog.dismiss();
                render();
            });
        });
        showStyledDialog(dialog);
    }

    private RadioButton createModeButton(String text) {
        RadioButton button = new RadioButton(this);
        button.setText(text);
        button.setId(View.generateViewId());
        return button;
    }

    private void setSpinnerValues(Spinner spinner, String[] values, String selected) {
        spinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, values));
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(selected)) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private void setBoOptions(Spinner spinner, String selectedMode, int preferredBo) {
        String[] values = selectedMode.equals("entertainment")
                ? new String[]{"BO1", "BO3", "BO5", "BO7"}
                : new String[]{"BO3", "BO5", "BO7"};
        setSpinnerValues(spinner, values, "BO" + preferredBo);
        if (spinner.getSelectedItemPosition() < 0) spinner.setSelection(0);
    }

    private void updateSetupFields(RadioGroup modeGroup, RadioButton regular, RadioButton entertainment,
                                   RadioButton wheel, RadioButton doubles, RadioButton team,
                                   EditText one, EditText two, EditText pool, EditText doubleOne,
                                   EditText doubleTwo, EditText teamOneInput, EditText teamTwoInput,
                                   EditText teamOneMembers, EditText teamTwoMembers, EditText teamRoster,
                                   Spinner teamTarget,
                                   Spinner score, Spinner serve, Spinner bo) {
        int checkedId = modeGroup.getCheckedRadioButtonId();
        boolean isEntertainment = checkedId == entertainment.getId();
        boolean isWheel = checkedId == wheel.getId();
        boolean isDoubles = checkedId == doubles.getId();
        boolean isTeam = checkedId == team.getId();
        boolean isSingle = !isWheel && !isDoubles && !isTeam;
        one.setVisibility(isSingle ? View.VISIBLE : View.GONE);
        two.setVisibility(isSingle ? View.VISIBLE : View.GONE);
        pool.setVisibility(isWheel ? View.VISIBLE : View.GONE);
        doubleOne.setVisibility(isDoubles ? View.VISIBLE : View.GONE);
        doubleTwo.setVisibility(isDoubles ? View.VISIBLE : View.GONE);
        teamOneInput.setVisibility(isTeam || isDoubles ? View.VISIBLE : View.GONE);
        teamTwoInput.setVisibility(isTeam || isDoubles ? View.VISIBLE : View.GONE);
        teamOneMembers.setVisibility(isTeam ? View.VISIBLE : View.GONE);
        teamTwoMembers.setVisibility(isTeam ? View.VISIBLE : View.GONE);
        teamRoster.setVisibility(isTeam ? View.VISIBLE : View.GONE);
        teamTarget.setVisibility(isTeam ? View.VISIBLE : View.GONE);
        score.setVisibility(isEntertainment ? View.VISIBLE : View.GONE);
        serve.setVisibility(isEntertainment ? View.VISIBLE : View.GONE);
        if (isWheel) setSpinnerValues(bo, new String[]{"BO1"}, "BO1");
        else setBoOptions(bo, isEntertainment ? "entertainment" : "regular",
                preferredSetupBestOf());
    }

    private int parseBestOf(Spinner spinner) {
        return Integer.parseInt(String.valueOf(spinner.getSelectedItem()).replaceAll("[^0-9]", ""));
    }

    private int preferredSetupBestOf() {
        return engine != null && engine.isStarted() ? engine.getBestOf() : defaultBestOf;
    }

    private int parseServeInterval(Spinner spinner) {
        String value = String.valueOf(spinner.getSelectedItem());
        if (value.contains("5")) return 5;
        if (value.contains("1")) return 1;
        return 2;
    }

    private String valueOrDefault(EditText input, String fallback) {
        String value = input.getText().toString().trim();
        return value.isEmpty() ? fallback : value;
    }

    private String normalizeMembers(String raw) {
        List<String> names = new ArrayList<>();
        for (String name : raw.split("\\r?\\n")) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty() && !names.contains(trimmed)) names.add(trimmed);
        }
        return String.join("\n", names);
    }

    private List<String> memberList(String encoded) {
        List<String> result = new ArrayList<>();
        for (String name : encoded.split("\\n")) {
            if (!name.trim().isEmpty()) result.add(name.trim());
        }
        return result;
    }

    private void parseTeamRosters(String raw, List<String> names, List<String> rosters) {
        for (String line : raw.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            String[] parts = trimmed.split("[=:：]", 2);
            if (parts.length != 2) continue;
            String name = parts[0].trim();
            String roster = normalizeMembers(parts[1].replace(',', '\n'));
            if (!name.isEmpty() && !names.contains(name) && memberList(roster).size() >= 2
                    && memberList(roster).size() <= 6) {
                names.add(name);
                rosters.add(roster);
            }
        }
    }

    private boolean validTeamRosters(List<String> rosters) {
        if (rosters.size() < 2) return false;
        for (String roster : rosters) {
            int count = memberList(roster).size();
            if (count < 2 || count > 6) return false;
        }
        return true;
    }

    private String encodeTeamRostersForInput() {
        if (teamNames.size() <= 2) return "";
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < teamNames.size(); i++) {
            if (result.length() > 0) result.append('\n');
            result.append(teamNames.get(i)).append('=').append(teamRosters.get(i).replace('\n', ','));
        }
        return result.toString();
    }

    private void showAddTeamMatchDialog() {
        if (!mode.equals("team")) return;
        if (teamNames.size() > 2) {
            showAddMultiTeamMatchDialog();
            return;
        }
        List<String> oneMembers = memberList(teamOneMembers);
        List<String> twoMembers = memberList(teamTwoMembers);
        if (oneMembers.isEmpty() || twoMembers.isEmpty()) {
            showModernMatchSetup();
            return;
        }
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        content.setPadding(pad, dp(8), pad, 0);
        Spinner oneSpinner = new Spinner(this);
        Spinner twoSpinner = new Spinner(this);
        oneSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, oneMembers));
        twoSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, twoMembers));
        content.addView(oneSpinner);
        content.addView(twoSpinner);
        String action = engine != null && engine.isStarted() && !engine.isFinished()
                ? "加入待赛" : "添加并开始";
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("添加团队赛小场")
                .setMessage("双方各选择 1 名已登记队员；当前比赛进行中时只加入待赛队列")
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton(action, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String pairing = oneSpinner.getSelectedItem().toString() + "|"
                    + twoSpinner.getSelectedItem().toString();
            if (teamPending.contains(pairing)) {
                toast("该对阵已经在待赛队列中");
                return;
            }
            teamPending.add(pairing);
            persistTeamSession();
            boolean active = engine != null && engine.isStarted() && !engine.isFinished();
            dialog.dismiss();
            if (!active) startTeamPair(0);
            else render();
        }));
        showStyledDialog(dialog);
    }

    private void showAddMultiTeamMatchDialog() {
        List<String> keys = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (int teamIndex = 0; teamIndex < teamNames.size(); teamIndex++) {
            for (String member : memberList(teamRosters.get(teamIndex))) {
                keys.add(teamIndex + "\u001f" + member);
                labels.add(teamNames.get(teamIndex) + " · " + member);
            }
        }
        if (keys.size() < 4) {
            showModernMatchSetup();
            return;
        }
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), 0);
        Spinner leftSpinner = new Spinner(this);
        Spinner rightSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels);
        leftSpinner.setAdapter(adapter);
        rightSpinner.setAdapter(adapter);
        if (rightSpinner.getCount() > 1) rightSpinner.setSelection(1);
        content.addView(leftSpinner);
        content.addView(rightSpinner);
        String action = engine != null && engine.isStarted() && !engine.isFinished()
                ? "加入待赛" : "添加并开始";
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("添加团队赛小场")
                .setMessage("选择任意两支不同队伍的队员；当前比赛进行中时加入待赛队列")
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton(action, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String[] left = keys.get(leftSpinner.getSelectedItemPosition()).split("\u001f", 2);
            String[] right = keys.get(rightSpinner.getSelectedItemPosition()).split("\u001f", 2);
            if (left[0].equals(right[0])) {
                toast("请选择不同队伍");
                return;
            }
            String pairing = teamNames.get(Integer.parseInt(left[0])) + "\u001f" + left[1]
                    + "\u001f" + teamNames.get(Integer.parseInt(right[0])) + "\u001f" + right[1];
            if (teamPending.contains(pairing)) {
                toast("该对阵已经在待赛队列中");
                return;
            }
            teamPending.add(pairing);
            persistTeamSession();
            boolean active = engine != null && engine.isStarted() && !engine.isFinished();
            dialog.dismiss();
            if (!active) startTeamPair(0);
            else render();
        }));
        showStyledDialog(dialog);
    }

    private void startTeamPair(int index) {
        if (index < 0 || index >= teamPending.size()) {
            render();
            return;
        }
        String encodedPairing = teamPending.remove(index);
        String[] pairing = encodedPairing.split("\u001f", -1);
        if (pairing.length == 4) {
            teamOne = pairing[0];
            playerOne = pairing[1];
            teamTwo = pairing[2];
            playerTwo = pairing[3];
        } else {
            String[] legacyPairing = encodedPairing.split("\\|", 2);
            playerOne = legacyPairing[0];
            playerTwo = legacyPairing.length > 1 ? legacyPairing[1] : "待定";
        }
        syncPrimaryTeamScores();
        targetScore = 11;
        serveInterval = 2;
        engine = new MatchEngine(3, targetScore, serveInterval);
        engine.start();
        persistTeamSession();
        persist();
        render();
    }

    private void finishTeamMatch() {
        int winner = engine.getWinsOne() > engine.getWinsTwo() ? 0 : 1;
        if (teamNames.size() > 2) {
            int winningTeamIndex = teamNames.indexOf(winner == 0 ? teamOne : teamTwo);
            if (winningTeamIndex >= 0) {
                while (teamWins.size() < teamNames.size()) teamWins.add(0);
                teamWins.set(winningTeamIndex, teamWins.get(winningTeamIndex) + 1);
            }
            syncPrimaryTeamScores();
        } else if (winner == 0) {
            teamWinsOne++;
        } else {
            teamWinsTwo++;
        }
        persistTeamSession();
        persist();
        String winnerName = winner == 0 ? teamOne : teamTwo;
        String historyEntry = "[团队赛] " + teamOne + " vs " + teamTwo
                + " · 小场 " + playerOne + " vs " + playerTwo
                + " · " + engine.getWinsOne() + ":" + engine.getWinsTwo()
                + " · 获胜：" + winnerName;
        appendHistoryEntry(historyEntry);
        boolean teamFinished = teamNames.size() > 2
                ? hasTeamReachedTarget()
                : teamWinsOne >= teamTargetWins || teamWinsTwo >= teamTargetWins;
        String action = teamPending.isEmpty() ? "添加下一场" : "开始下一场";
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("团队赛小场结束")
                .setMessage(playerOne + " vs " + playerTwo + "\n小场获胜：" + winnerName
                        + "\n团队比分：" + teamStandings()
                        + "\n目标：先胜 " + teamTargetWins + " 场"
                        + (teamFinished ? "\n\n团队赛已结束" : "")
                        + "\n\n关闭此弹窗后，页面底部仍保留“待赛 + 添加对局”入口。")
                .setNegativeButton("关闭", (d, w) -> render())
                .setPositiveButton(teamFinished ? "完成团队赛" : action, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            dialog.dismiss();
            if (teamFinished) {
                clearActive();
            } else if (teamPending.isEmpty()) showAddTeamMatchDialog();
            else startTeamPair(0);
        }));
        showStyledDialog(dialog);
    }

    private void showWheelPairChooser() {
        if (wheelPlayers.size() < 2) return;
        List<String> pairs = new ArrayList<>();
        for (int i = 0; i < wheelPlayers.size(); i++) {
            for (int j = i + 1; j < wheelPlayers.size(); j++) {
                pairs.add(wheelPlayers.get(i) + "  vs  " + wheelPlayers.get(j));
            }
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("选择下一场 PK")
                .setItems(pairs.toArray(new String[0]), (ignored, which) -> {
                    String[] selected = pairs.get(which).split("  vs  ");
                    playerOne = selected[0].trim();
                    playerTwo = selected[1].trim();
                    targetScore = 11;
                    serveInterval = 2;
                    engine = new MatchEngine(1, targetScore, serveInterval);
                    engine.start();
                    persist();
                    render();
                })
                .setNegativeButton("取消", null)
                .create();
        showStyledDialog(dialog);
    }

    private void showServeChooser() {
        if (engine == null || !engine.isStarted() || engine.getPauseOwner() >= 0) return;
        if (mode.equals("doubles")) {
            showDoublesServeChooser();
            return;
        }
        String[] choices = {playerOne + "（当前 " + engine.getCurrentOne() + " 分）",
                playerTwo + "（当前 " + engine.getCurrentTwo() + " 分）"};
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("选择发球方")
                .setItems(choices, (ignored, which) -> {
                    engine.setCurrentServer(which);
                    persist();
                    render();
                })
                .setNegativeButton("取消", null)
                .create();
        showStyledDialog(dialog);
    }

    private void showDoublesServeChooser() {
        List<String> oneMembers = memberList(doubleOneMembers);
        List<String> twoMembers = memberList(doubleTwoMembers);
        List<String> choices = new ArrayList<>();
        for (String member : oneMembers) choices.add(teamOne + " · " + member);
        for (String member : twoMembers) choices.add(teamTwo + " · " + member);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("选择发球方")
                .setMessage("选择发球者后，接发者和搭档按双打轮转规则更新")
                .setItems(choices.toArray(new String[0]), (ignored, which) -> {
                    if (which < oneMembers.size()) {
                        doubleServer = oneMembers.get(which);
                        doubleReceiver = twoMembers.get(0);
                        engine.setCurrentServer(0);
                    } else {
                        int index = which - oneMembers.size();
                        doubleServer = twoMembers.get(index);
                        doubleReceiver = oneMembers.get(0);
                        engine.setCurrentServer(1);
                    }
                    lastServer = engine.getCurrentServer();
                    persistDoubleSession();
                    persist();
                    render();
                })
                .setNegativeButton("取消", null)
                .create();
        showStyledDialog(dialog);
    }

    private LinearLayout createPausePlayerCard(int player) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        card.setBackgroundResource(engine.isPauseUsed(player)
                ? R.drawable.bg_set_tab : R.drawable.bg_set_current);
        card.setTag(player);
        TextView name = new TextView(this);
        name.setText(player == 0 ? playerOne : playerTwo);
        name.setTextColor(getColor(R.color.score_ink));
        name.setTextSize(18);
        name.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        card.addView(name, new LinearLayout.LayoutParams(-1, dp(34)));
        TextView score = new TextView(this);
        score.setText("总比分  " + engine.getWinsOne() + ":" + engine.getWinsTwo()
                + "    本局  " + engine.getCurrentOne() + ":" + engine.getCurrentTwo());
        score.setTextColor(getColor(R.color.score_muted));
        score.setTextSize(14);
        card.addView(score, new LinearLayout.LayoutParams(-1, dp(30)));
        TextView pause = new TextView(this);
        pause.setText(engine.isPauseUsed(player) ? "● 本场暂停已用" : "○ 本场暂停可用");
        pause.setTextColor(getColor(engine.isPauseUsed(player)
                ? R.color.score_muted : R.color.score_blue));
        pause.setTextSize(14);
        card.addView(pause, new LinearLayout.LayoutParams(-1, dp(28)));
        card.setAlpha(engine.isPauseUsed(player) ? 0.58f : 1f);
        return card;
    }

    private void finishPauseEarly(AlertDialog dialog) {
        if (engine == null || engine.getPauseOwner() < 0) return;
        engine.resume();
        pauseStartedAt = 0L;
        pauseHandler.removeCallbacks(pauseExpiry);
        pauseHandler.removeCallbacks(pauseTicker);
        persist();
        dialog.dismiss();
        render();
    }

    private void showPauseLockedDialog() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), dp(8));
        TextView description = new TextView(this);
        description.setText("计分、换发和本场设定已锁定。倒计时结束后比赛会自动恢复。");
        description.setTextColor(getColor(R.color.score_muted));
        description.setTextSize(15);
        description.setLineSpacing(dp(3), 1f);
        content.addView(description, new LinearLayout.LayoutParams(-1, -2));
        TextView timer = new TextView(this);
        timer.setGravity(Gravity.CENTER);
        timer.setTextColor(getColor(R.color.score_red));
        timer.setTextSize(38);
        timer.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        timer.setBackgroundResource(R.drawable.bg_pause_timer);
        content.addView(timer, new LinearLayout.LayoutParams(-1, dp(82)));
        TextView owner = new TextView(this);
        owner.setGravity(Gravity.CENTER);
        owner.setText("暂停方：" + pauseOwnerName());
        owner.setTextColor(getColor(R.color.score_ink));
        owner.setTextSize(15);
        content.addView(owner, new LinearLayout.LayoutParams(-1, dp(40)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("暂停锁定")
                .setView(content)
                .setNegativeButton("关闭", null)
                .setPositiveButton("提前结束暂停", null)
                .create();
        Runnable timerTicker = new Runnable() {
            @Override
            public void run() {
                if (!dialog.isShowing() || engine == null || engine.getPauseOwner() < 0) return;
                timer.setText(formatPauseRemaining());
                pauseHandler.postDelayed(this, 1_000L);
            }
        };
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> finishPauseEarly(dialog));
            timerTicker.run();
        });
        dialog.setOnDismissListener(ignored -> pauseHandler.removeCallbacks(timerTicker));
        showStyledDialog(dialog);
    }

    private void showModernPauseChooser() {
        if (engine == null || !engine.isStarted()) {
            toast("请先开始比赛");
            return;
        }
        if (engine.getPauseOwner() >= 0) {
            showPauseLockedDialog();
            return;
        }
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(8), dp(18), dp(8));
        TextView description = new TextView(this);
        description.setText("每位选手本场限用 1 次，暂停时长最多 60 秒。点击对应卡片开始暂停。");
        description.setTextColor(getColor(R.color.score_muted));
        description.setTextSize(14);
        description.setLineSpacing(dp(3), 1f);
        content.addView(description, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout oneCard = createPausePlayerCard(0);
        LinearLayout twoCard = createPausePlayerCard(1);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.topMargin = dp(10);
        content.addView(oneCard, cardParams);
        LinearLayout.LayoutParams secondParams = new LinearLayout.LayoutParams(-1, -2);
        secondParams.topMargin = dp(8);
        content.addView(twoCard, secondParams);
        AlertDialog[] holder = new AlertDialog[1];
        View.OnClickListener select = v -> {
            int player = (Integer) v.getTag();
            if (engine.isPauseUsed(player)) {
                toast("该方本场暂停已用完");
                return;
            }
            engine.pause(player);
            pauseStartedAt = System.currentTimeMillis();
            schedulePauseExpiry();
            persist();
            holder[0].dismiss();
            render();
        };
        oneCard.setOnClickListener(select);
        twoCard.setOnClickListener(select);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("选择暂停方")
                .setView(content)
                .setNegativeButton("取消", null)
                .create();
        holder[0] = dialog;
        showStyledDialog(dialog);
    }

    private void showPauseChooser() {
        if (engine == null || !engine.isStarted()) {
            toast("请先开始比赛");
            return;
        }
        if (engine.getPauseOwner() >= 0) {
            long remaining = Math.max(0L, 60L - (System.currentTimeMillis() - pauseStartedAt) / 1000L);
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("暂停中")
                    .setMessage("所有计分、换发和本场设定已锁定\n剩余约 " + remaining + " 秒")
                    .setPositiveButton("提前结束暂停", (d, w) -> {
                        engine.resume();
                        pauseStartedAt = 0L;
                        pauseHandler.removeCallbacks(pauseExpiry);
                        persist();
                        render();
                    })
                    .create();
            showStyledDialog(dialog);
            return;
        }
        String[] choices = {
                playerOne + "  " + engine.getCurrentOne() + ":" + engine.getCurrentTwo()
                        + "  " + (engine.isPauseUsed(0) ? "● 已用" : "○ 可用"),
                playerTwo + "  " + engine.getCurrentOne() + ":" + engine.getCurrentTwo()
                        + "  " + (engine.isPauseUsed(1) ? "● 已用" : "○ 可用")};
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("选择暂停方")
                .setItems(choices, (ignored, which) -> {
                    if (engine.isPauseUsed(which)) {
                        toast("该方本场暂停已用完");
                        return;
                    }
                    engine.pause(which);
                    pauseStartedAt = System.currentTimeMillis();
                    schedulePauseExpiry();
                    persist();
                    render();
                })
                .setNegativeButton("取消", null)
                .create();
        showStyledDialog(dialog);
    }

    private void showLegacyFinishMatch() {
        persist();
        String winner = engine.getWinsOne() > engine.getWinsTwo() ? playerOne : playerTwo;
        StringBuilder result = new StringBuilder("最终大比分：")
                .append(engine.getWinsOne()).append(":").append(engine.getWinsTwo())
                .append("\n获胜方：").append(winner).append("\n\n小局记录：");
        for (MatchEngine.GameRecord record : engine.getGameRecords()) {
            result.append("\n").append(record.playerOne).append(":").append(record.playerTwo)
                    .append("  ").append(record.winner == 0 ? playerOne : playerTwo).append("胜");
        }
        if (mode.equals("wheel")) {
            appendWheelHistory(winner + " " + engine.getWinsOne() + ":" + engine.getWinsTwo());
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("比赛结束")
                .setMessage(result.toString())
                .setNegativeButton("完成", (d, w) -> clearActive())
                .setPositiveButton(mode.equals("wheel") ? "下一场 PK" : "再来一场", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            clearActive();
            dialog.dismiss();
            if (mode.equals("wheel")) showWheelPairChooser();
            else showModernMatchSetup();
        }));
        showStyledDialog(dialog);
    }

    private void finishMatch() {
        persist();
        int winnerIndex = engine.getWinsOne() > engine.getWinsTwo() ? 0 : 1;
        String winner = winnerIndex == 0 ? playerOne : playerTwo;
        String loser = winnerIndex == 0 ? playerTwo : playerOne;
        StringBuilder history = new StringBuilder("[")
                .append(historyLabel()).append("] ")
                .append(playerOne).append(" vs ").append(playerTwo)
                .append(" · BO").append(engine.getBestOf())
                .append(" · 大比分 ").append(engine.getWinsOne()).append(":").append(engine.getWinsTwo())
                .append(" · 获胜：").append(winner);
        for (int i = 0; i < engine.getGameRecords().size(); i++) {
            MatchEngine.GameRecord record = engine.getGameRecords().get(i);
            history.append("\n第 ").append(i + 1).append(" 局：")
                    .append(record.playerOne).append(":").append(record.playerTwo)
                    .append(" · ").append(record.winner == 0 ? playerOne : playerTwo).append("胜");
        }
        appendHistoryEntry(history.toString());

        LinearLayout resultContent = new LinearLayout(this);
        resultContent.setOrientation(LinearLayout.VERTICAL);
        resultContent.setPadding(dp(20), dp(8), dp(20), dp(8));
        TextView overall = new TextView(this);
        overall.setText("最终大比分  " + engine.getWinsOne() + ":" + engine.getWinsTwo()
                + "\n获胜方：" + winner);
        overall.setTextColor(getColor(R.color.score_red));
        overall.setTextSize(20);
        overall.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        resultContent.addView(overall);
        TextView loserView = new TextView(this);
        loserView.setText("落败方：" + loser);
        loserView.setTextColor(getColor(R.color.score_muted));
        loserView.setTextSize(15);
        loserView.setPadding(0, dp(4), 0, dp(10));
        resultContent.addView(loserView);
        TextView label = new TextView(this);
        label.setText("小局记录");
        label.setTextColor(getColor(R.color.score_ink));
        label.setTextSize(16);
        label.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        resultContent.addView(label);
        for (int i = 0; i < engine.getGameRecords().size(); i++) {
            MatchEngine.GameRecord record = engine.getGameRecords().get(i);
            TextView row = new TextView(this);
            row.setText("第 " + (i + 1) + " 局  " + record.playerOne + ":" + record.playerTwo
                    + "  ·  " + (record.winner == 0 ? playerOne : playerTwo) + "胜");
            row.setTextColor(getColor(record.winner == winnerIndex
                    ? R.color.score_red : R.color.score_muted));
            row.setTextSize(15);
            row.setPadding(0, dp(5), 0, dp(5));
            resultContent.addView(row);
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("比赛结束")
                .setView(resultContent)
                .setNegativeButton("完成", (d, w) -> clearActive())
                .setPositiveButton(mode.equals("wheel") ? "下一场 PK" : "再来一场", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            clearActive();
            dialog.dismiss();
            if (mode.equals("wheel")) showWheelPairChooser();
            else showModernMatchSetup();
        }));
        showStyledDialog(dialog);
    }

    private void showHistory() {
        String history = prefs.getString(KEY_HISTORY, "");
        if (history.isEmpty()) {
            AlertDialog empty = new AlertDialog.Builder(this)
                    .setTitle("比赛记录")
                    .setMessage("暂无比赛记录")
                    .setPositiveButton("关闭", null)
                    .create();
            showStyledDialog(empty);
            return;
        }
        String[] entries = history.split("\\n\\n");
        String[] summaries = new String[entries.length];
        for (int i = 0; i < entries.length; i++) {
            String[] lines = entries[i].split("\\r?\\n", 2);
            summaries[i] = lines[0];
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("全部比赛记录")
                .setItems(summaries, (ignored, which) -> showHistoryDetail(entries[which]))
                .setNegativeButton("关闭", null)
                .create();
        showStyledDialog(dialog);
    }

    private void showHistoryDetail(String entry) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("比赛详情")
                .setMessage(entry)
                .setPositiveButton("关闭", null)
                .create();
        showStyledDialog(dialog);
    }

    private void showScoreAdjustDialog() {
        if (engine == null || selectedGameIndex >= engine.getGameRecords().size()) return;
        MatchEngine.GameRecord record = engine.getGameRecords().get(selectedGameIndex);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setPadding(dp(20), dp(8), dp(20), 0);
        EditText one = new EditText(this);
        one.setInputType(InputType.TYPE_CLASS_NUMBER);
        one.setHint("玩家 1");
        one.setText(String.valueOf(record.playerOne));
        EditText two = new EditText(this);
        two.setInputType(InputType.TYPE_CLASS_NUMBER);
        two.setHint("玩家 2");
        two.setText(String.valueOf(record.playerTwo));
        content.addView(one, new LinearLayout.LayoutParams(0, dp(56), 1));
        content.addView(two, new LinearLayout.LayoutParams(0, dp(56), 1));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("调整第 " + (selectedGameIndex + 1) + " 局赛果")
                .setMessage("请输入达到胜负条件的最终小局比分")
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                int scoreOne = Integer.parseInt(one.getText().toString().trim());
                int scoreTwo = Integer.parseInt(two.getText().toString().trim());
                engine.adjustGameRecord(selectedGameIndex, scoreOne, scoreTwo);
                persist();
                render();
                dialog.dismiss();
            } catch (IllegalArgumentException e) {
                toast("比分必须达到 " + engine.getTargetScore() + " 分且领先 2 分");
            }
        }));
        showStyledDialog(dialog);
    }

    private void showGlobalSettings() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), 0);
        CheckBox undo = new CheckBox(this);
        undo.setText("加减分后显示撤销提示");
        undo.setChecked(undoPrompt);
        content.addView(undo);
        CheckBox sound = new CheckBox(this);
        sound.setText("开启声音反馈");
        sound.setChecked(soundEnabled);
        content.addView(sound);
        CheckBox vibration = new CheckBox(this);
        vibration.setText("开启震动反馈");
        vibration.setChecked(vibrationEnabled);
        content.addView(vibration);
        TextView defaultLabel = new TextView(this);
        defaultLabel.setText("默认进入方式");
        defaultLabel.setTextColor(getColor(R.color.score_muted));
        defaultLabel.setTextSize(13);
        defaultLabel.setPadding(0, dp(8), 0, 0);
        content.addView(defaultLabel);
        Spinner defaultSpinner = new Spinner(this);
        String[] modes = {"正规赛", "娱乐赛", "车轮赛", "双打", "团队赛"};
        setSpinnerValues(defaultSpinner, modes, modeDisplayName(defaultMode));
        content.addView(defaultSpinner);
        TextView presetLabel = new TextView(this);
        presetLabel.setText("已保存个人配置");
        presetLabel.setTextColor(getColor(R.color.score_muted));
        presetLabel.setTextSize(13);
        presetLabel.setPadding(0, dp(8), 0, 0);
        content.addView(presetLabel);
        Spinner presetSpinner = new Spinner(this);
        List<String> presets = savedPresetNames();
        List<String> presetOptions = new ArrayList<>();
        presetOptions.add("不加载配置");
        presetOptions.addAll(presets);
        setSpinnerValues(presetSpinner, presetOptions.toArray(new String[0]),
                prefs.getString("last_preset", ""));
        content.addView(presetSpinner);
        EditText presetName = new EditText(this);
        presetName.setHint("保存个人配置名称（可选）");
        content.addView(presetName);
        TextView presetHint = new TextView(this);
        presetHint.setText("保存后可在本场设定中继续使用当前参数；声音、震动和撤销提示为全局设置。");
        presetHint.setTextColor(getColor(R.color.score_muted));
        presetHint.setTextSize(13);
        presetHint.setPadding(0, dp(4), 0, 0);
        content.addView(presetHint);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("全局设置")
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            undoPrompt = undo.isChecked();
            soundEnabled = sound.isChecked();
            vibrationEnabled = vibration.isChecked();
            defaultMode = modeFromDisplayName(String.valueOf(defaultSpinner.getSelectedItem()));
            String selectedPreset = String.valueOf(presetSpinner.getSelectedItem());
            SharedPreferences.Editor editor = prefs.edit()
                    .putBoolean(KEY_UNDO_PROMPT, undoPrompt)
                    .putBoolean(KEY_SOUND, soundEnabled)
                    .putBoolean(KEY_VIBRATION, vibrationEnabled)
                    .putString(KEY_DEFAULT_MODE, defaultMode)
                    .putInt("default_bo", defaultBestOf);
            if (!selectedPreset.equals("不加载配置") && presets.contains(selectedPreset)) {
                String prefix = "preset_" + selectedPreset;
                defaultMode = prefs.getString(prefix + "_mode", defaultMode);
                defaultBestOf = prefs.getInt(prefix + "_bo", defaultBestOf);
                if (engine == null || !engine.isStarted()) {
                    targetScore = prefs.getInt(prefix + "_target", targetScore);
                    serveInterval = prefs.getInt(prefix + "_serve", serveInterval);
                }
                editor.putString(KEY_DEFAULT_MODE, defaultMode)
                        .putInt("default_bo", defaultBestOf)
                        .putString("last_preset", selectedPreset);
            }
            String name = presetName.getText().toString().trim();
            if (!name.isEmpty()) {
                String prefix = "preset_" + name;
                editor.putString(prefix + "_mode", mode)
                        .putInt(prefix + "_bo", engine == null ? defaultBestOf : engine.getBestOf())
                        .putInt(prefix + "_target", engine == null ? targetScore : engine.getTargetScore())
                        .putInt(prefix + "_serve", engine == null ? serveInterval : engine.getServeInterval())
                        .putString("last_preset", name);
            }
            editor.apply();
            toast(name.isEmpty() ? "全局设置已保存" : "全局设置和个人配置已保存");
            dialog.dismiss();
        }));
        showStyledDialog(dialog);
    }

    private void showModeInfo() {
        String title;
        String message;
        if (mode.equals("doubles")) {
            title = "双打规则说明";
            message = "1. 两队各 2 名选手，比分按队伍累计。\n"
                    + "2. 每局开始确认首发发球员和首位接发员。\n"
                    + "3. 每次换发后，上一回合接发者成为发球者，上一回合发球者的搭档成为接发者。\n"
                    + "4. 发球后双方队员按固定顺序交替击球。\n"
                    + "5. 决胜局一方先到 5 分时交换场地，并按规则调整接发顺序。";
        } else {
            title = "团队赛规则说明";
            message = "1. 每队登记 2-6 名队员。\n"
                    + "2. 点击“待赛 + 添加对局”创建单打小场，比赛进行中也可以继续加入待赛。\n"
                    + "3. 每场小场结束后累计团队胜场，先达到目标胜场的队伍获胜。\n"
                    + "4. 可在团队设定中录入多支队伍，待赛入口支持任意两队配对。";
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("知道了", null)
                .create();
        showStyledDialog(dialog);
    }

    private void showUndoSnackbar(int player, boolean add) {
        if (!undoPrompt || engine == null || engine.isFinished()) return;
        Snackbar.make(findViewById(R.id.main),
                        (add ? "已给 " : "已扣 ") + (player == 0 ? playerOne : playerTwo) + " 1 分",
                        Snackbar.LENGTH_LONG)
                .setAction("撤销", v -> {
                    if (engine.undoLastAction()) {
                        selectedGameIndex = engine.getGameRecords().size();
                        persist();
                        render();
                    }
                }).show();
    }

    private void playFeedback() {
        if (soundEnabled) {
            ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 75);
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 70);
            tone.release();
        }
        if (vibrationEnabled) {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(35, 80));
                } else {
                    vibrator.vibrate(35);
                }
            }
        }
    }

    private void showStyledDialog(AlertDialog dialog) {
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(R.drawable.bg_dialog);
            window.setDimAmount(0.38f);
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            int width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(32), dp(440));
            window.setLayout(width, android.view.WindowManager.LayoutParams.WRAP_CONTENT);
        }
        int titleId = getResources().getIdentifier("alertTitle", "id", "android");
        TextView title = titleId == 0 ? null : dialog.findViewById(titleId);
        if (title != null) {
            title.setTextColor(getColor(R.color.score_ink));
            title.setTextSize(20);
        }
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) {
            message.setTextColor(getColor(R.color.score_muted));
            message.setTextSize(15);
            message.setLineSpacing(dp(3), 1.0f);
        }
        styleDialogButton(dialog.getButton(AlertDialog.BUTTON_POSITIVE), R.color.score_blue);
        styleDialogButton(dialog.getButton(AlertDialog.BUTTON_NEGATIVE), R.color.score_muted);
        styleDialogButton(dialog.getButton(AlertDialog.BUTTON_NEUTRAL), R.color.score_muted);
        ListView list = dialog.getListView();
        if (list != null) {
            list.setDivider(new ColorDrawable(0xFFE6EDF2));
            list.setDividerHeight(1);
            list.setPadding(dp(8), dp(6), dp(8), dp(6));
        }
    }

    private void styleDialogButton(Button button, int colorResource) {
        if (button == null) return;
        button.setAllCaps(false);
        button.setTextColor(getColor(colorResource));
        button.setTextSize(15);
    }

    private void persist() {
        if (engine == null) return;
        viewModel.update(engine, mode, playerOne, playerTwo);
        StringBuilder records = new StringBuilder();
        for (MatchEngine.GameRecord record : engine.getGameRecords()) {
            if (records.length() > 0) records.append(';');
            records.append(record.playerOne).append(',').append(record.playerTwo).append(',').append(record.winner);
        }
        prefs.edit().putBoolean(KEY_ACTIVE, engine.isStarted() && !engine.isFinished())
                .putString(KEY_MODE, mode)
                .putString(KEY_PLAYERS, playerOne + "\n" + playerTwo)
                .putInt("bo", engine.getBestOf())
                .putInt("target", engine.getTargetScore())
                .putInt("serve_interval", engine.getServeInterval())
                .putInt("one", engine.getCurrentOne())
                .putInt("two", engine.getCurrentTwo())
                .putInt("wins_one", engine.getWinsOne())
                .putInt("wins_two", engine.getWinsTwo())
                .putInt("starting_server", engine.getStartingServer())
                .putInt("pause_owner", engine.getPauseOwner())
                .putBoolean("pause_one", engine.isPauseUsed(0))
                .putBoolean("pause_two", engine.isPauseUsed(1))
                .putBoolean("started", engine.isStarted())
                .putLong("pause_started_at", pauseStartedAt)
                .putString("double_one_members", doubleOneMembers)
                .putString("double_two_members", doubleTwoMembers)
                .putString("double_server", doubleServer)
                .putString("double_receiver", doubleReceiver)
                .putString("records", records.toString())
                .apply();
    }

    private void loadSavedMatch() {
        mode = prefs.getString(KEY_MODE, defaultMode);
        if (mode.equals("team")) loadTeamSession();
        if (mode.equals("doubles")) loadDoubleSession();
        if (!prefs.getBoolean(KEY_ACTIVE, false)) {
            int defaultBo = mode.equals("wheel") ? 1 : 3;
            int defaultTarget = mode.equals("entertainment") ? targetScore : MatchEngine.REGULAR_TARGET;
            int defaultServe = mode.equals("entertainment") ? serveInterval : 2;
            engine = new MatchEngine(defaultBo, defaultTarget, defaultServe);
            return;
        }
        if (mode.equals("wheel")) {
            String savedPool = prefs.getString("wheel_players", "");
            if (!savedPool.isEmpty()) {
                wheelPlayers.clear();
                wheelPlayers.addAll(Arrays.asList(savedPool.split("\\n")));
            }
        }
        String[] names = prefs.getString(KEY_PLAYERS, "玩家 1\n玩家 2").split("\\n", 2);
        playerOne = names.length > 0 ? names[0] : "玩家 1";
        playerTwo = names.length > 1 ? names[1] : "玩家 2";
        targetScore = prefs.getInt("target", 11);
        serveInterval = prefs.getInt("serve_interval", 2);
        pauseStartedAt = prefs.getLong("pause_started_at", 0L);
        engine = new MatchEngine(prefs.getInt("bo", 3), targetScore, serveInterval);
        List<MatchEngine.GameRecord> records = new ArrayList<>();
        String encoded = prefs.getString("records", "");
        if (!encoded.isEmpty()) {
            for (String row : encoded.split(";")) {
                String[] values = row.split(",");
                if (values.length == 3) {
                    records.add(new MatchEngine.GameRecord(Integer.parseInt(values[0]),
                            Integer.parseInt(values[1]), Integer.parseInt(values[2])));
                }
            }
        }
        engine.restoreState(prefs.getInt("one", 0), prefs.getInt("two", 0),
                prefs.getInt("wins_one", 0), prefs.getInt("wins_two", 0),
                prefs.getInt("starting_server", 0), prefs.getInt("pause_owner", -1),
                prefs.getBoolean("pause_one", false), prefs.getBoolean("pause_two", false),
                prefs.getBoolean("started", true), false, records);
        selectedGameIndex = records.size();
        if (engine.getPauseOwner() >= 0) schedulePauseExpiry();
    }

    private void clearActive() {
        prefs.edit().putBoolean(KEY_ACTIVE, false).apply();
        int bestOf = engine == null ? (mode.equals("wheel") ? 1 : 3) : engine.getBestOf();
        int target = engine == null ? MatchEngine.REGULAR_TARGET : engine.getTargetScore();
        int serve = engine == null ? 2 : engine.getServeInterval();
        engine = new MatchEngine(bestOf, target, serve);
        pauseStartedAt = 0L;
        pauseHandler.removeCallbacks(pauseExpiry);
        selectedGameIndex = 0;
        viewModel.update(engine, mode, playerOne, playerTwo);
        render();
    }

    private void persistWheelPlayers() {
        prefs.edit().putString("wheel_players", String.join("\n", wheelPlayers)).apply();
    }

    private void persistTeamSession() {
        prefs.edit()
                .putString("team_one", teamOne)
                .putString("team_two", teamTwo)
                .putString("team_one_members", teamOneMembers)
                .putString("team_two_members", teamTwoMembers)
                .putInt("team_wins_one", teamWinsOne)
                .putInt("team_wins_two", teamWinsTwo)
                .putInt("team_target_wins", teamTargetWins)
                .putString("team_names", String.join("\u001f", teamNames))
                .putString("team_rosters", String.join("\u001e", teamRosters))
                .putString("team_wins", joinTeamWins())
                .putString(KEY_TEAM_PENDING, String.join("\n", teamPending))
                .apply();
    }

    private void loadTeamSession() {
        teamOne = prefs.getString("team_one", "队伍 1");
        teamTwo = prefs.getString("team_two", "队伍 2");
        teamOneMembers = prefs.getString("team_one_members", "");
        teamTwoMembers = prefs.getString("team_two_members", "");
        teamWinsOne = prefs.getInt("team_wins_one", 0);
        teamWinsTwo = prefs.getInt("team_wins_two", 0);
        teamTargetWins = prefs.getInt("team_target_wins", 2);
        teamNames.clear();
        teamRosters.clear();
        teamWins.clear();
        String encodedNames = prefs.getString("team_names", "");
        String encodedRosters = prefs.getString("team_rosters", "");
        if (!encodedNames.isEmpty() && !encodedRosters.isEmpty()) {
            teamNames.addAll(Arrays.asList(encodedNames.split("\u001f", -1)));
            teamRosters.addAll(Arrays.asList(encodedRosters.split("\u001e", -1)));
            for (String score : prefs.getString("team_wins", "").split(",")) {
                if (!score.trim().isEmpty()) teamWins.add(Integer.parseInt(score));
            }
        }
        if (teamNames.size() < 2 || teamRosters.size() != teamNames.size()) {
            teamNames.clear();
            teamRosters.clear();
            teamNames.add(teamOne);
            teamNames.add(teamTwo);
            teamRosters.add(teamOneMembers);
            teamRosters.add(teamTwoMembers);
        }
        while (teamWins.size() < teamNames.size()) teamWins.add(0);
        syncPrimaryTeamScores();
        teamPending.clear();
        String encoded = prefs.getString(KEY_TEAM_PENDING, "");
        if (!encoded.isEmpty()) teamPending.addAll(Arrays.asList(encoded.split("\n")));
    }

    private void appendHistoryEntry(String entry) {
        String old = prefs.getString(KEY_HISTORY, "");
        prefs.edit().putString(KEY_HISTORY, old.isEmpty() ? entry : old + "\n\n" + entry).apply();
    }

    private void appendWheelHistory(String entry) {
        appendHistoryEntry("[车轮赛] " + entry);
    }

    private String joinTeamWins() {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < teamWins.size(); i++) {
            if (i > 0) result.append(',');
            result.append(teamWins.get(i));
        }
        return result.toString();
    }

    private void syncPrimaryTeamScores() {
        if (teamNames.size() < 2) return;
        while (teamWins.size() < teamNames.size()) teamWins.add(0);
        int firstIndex = teamNames.indexOf(teamOne);
        int secondIndex = teamNames.indexOf(teamTwo);
        teamWinsOne = firstIndex >= 0 ? teamWins.get(firstIndex) : 0;
        teamWinsTwo = secondIndex >= 0 ? teamWins.get(secondIndex) : 0;
    }

    private boolean hasTeamReachedTarget() {
        for (Integer score : teamWins) {
            if (score >= teamTargetWins) return true;
        }
        return false;
    }

    private String teamStandings() {
        if (teamNames.size() <= 2) {
            return teamOne + " " + teamWinsOne + ":" + teamWinsTwo + " " + teamTwo;
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < teamNames.size(); i++) {
            if (i > 0) result.append("  ·  ");
            int score = i < teamWins.size() ? teamWins.get(i) : 0;
            result.append(teamNames.get(i)).append(' ').append(score);
        }
        return result.toString();
    }

    private void loadGlobalSettings() {
        undoPrompt = prefs.getBoolean(KEY_UNDO_PROMPT, true);
        soundEnabled = prefs.getBoolean(KEY_SOUND, true);
        vibrationEnabled = prefs.getBoolean(KEY_VIBRATION, true);
        defaultMode = prefs.getString(KEY_DEFAULT_MODE, "regular");
        defaultBestOf = prefs.getInt("default_bo", 3);
        targetScore = prefs.getInt("target", 11);
        serveInterval = prefs.getInt("serve_interval", 2);
        String lastPreset = prefs.getString("last_preset", "");
        if (!lastPreset.isEmpty()) {
            String prefix = "preset_" + lastPreset;
            defaultMode = prefs.getString(prefix + "_mode", defaultMode);
            defaultBestOf = prefs.getInt(prefix + "_bo", defaultBestOf);
            targetScore = prefs.getInt(prefix + "_target", targetScore);
            serveInterval = prefs.getInt(prefix + "_serve", serveInterval);
        }
    }

    private void loadDoubleSession() {
        doubleOneMembers = prefs.getString("double_one_members", "");
        doubleTwoMembers = prefs.getString("double_two_members", "");
        doubleServer = prefs.getString("double_server", "");
        doubleReceiver = prefs.getString("double_receiver", "");
    }

    private void persistDoubleSession() {
        prefs.edit()
                .putString("double_one_members", doubleOneMembers)
                .putString("double_two_members", doubleTwoMembers)
                .putString("double_server", doubleServer)
                .putString("double_receiver", doubleReceiver)
                .apply();
    }

    private void schedulePauseExpiry() {
        pauseHandler.removeCallbacks(pauseExpiry);
        pauseHandler.removeCallbacks(pauseTicker);
        if (pauseStartedAt <= 0L) pauseStartedAt = System.currentTimeMillis();
        long remaining = Math.max(1L, 60_000L - (System.currentTimeMillis() - pauseStartedAt));
        pauseHandler.postDelayed(pauseExpiry, remaining);
        pauseHandler.post(pauseTicker);
    }

    private String pauseOwnerName() {
        if (engine == null || engine.getPauseOwner() < 0) return "";
        return engine.getPauseOwner() == 0 ? playerOne : playerTwo;
    }

    private String formatPauseRemaining() {
        long seconds = Math.max(0L, 60L - (System.currentTimeMillis() - pauseStartedAt) / 1_000L);
        return String.format(java.util.Locale.US, "%02d:%02d", seconds / 60L, seconds % 60L);
    }

    private void ensureDoublesRotation() {
        List<String> oneMembers = memberList(doubleOneMembers);
        List<String> twoMembers = memberList(doubleTwoMembers);
        if (oneMembers.size() != 2 || twoMembers.size() != 2) return;
        if (!oneMembers.contains(doubleServer) && !twoMembers.contains(doubleServer)) {
            doubleServer = engine.getCurrentServer() == 0 ? oneMembers.get(0) : twoMembers.get(0);
        }
        if (!oneMembers.contains(doubleReceiver) && !twoMembers.contains(doubleReceiver)) {
            doubleReceiver = engine.getCurrentServer() == 0 ? twoMembers.get(0) : oneMembers.get(0);
        }
    }

    private void rotateDoublesIfNeeded() {
        if (!mode.equals("doubles")) return;
        ensureDoublesRotation();
        int currentServer = engine.getCurrentServer();
        if (lastServer >= 0 && currentServer != lastServer) {
            String previousServer = doubleServer;
            doubleServer = doubleReceiver;
            doubleReceiver = partnerForTeam(lastServer, previousServer);
        }
        lastServer = currentServer;
        persistDoubleSession();
    }

    private String partnerForTeam(int team, String member) {
        List<String> members = memberList(team == 0 ? doubleOneMembers : doubleTwoMembers);
        if (members.size() < 2) return member;
        return members.get(members.get(0).equals(member) ? 1 : 0);
    }

    private String memberSummary(String encoded) {
        List<String> members = memberList(encoded);
        return members.isEmpty() ? "" : String.join(" / ", members);
    }

    private String safeName(String value) {
        return value == null || value.isEmpty() ? "待定" : value;
    }

    private String historyLabel() {
        if (mode.equals("entertainment")) return "娱乐赛";
        if (mode.equals("wheel")) return "车轮赛";
        if (mode.equals("doubles")) return "双打";
        if (mode.equals("team")) return "团队赛";
        return "正规赛";
    }

    private String modeDisplayName(String value) {
        if (value.equals("entertainment")) return "娱乐赛";
        if (value.equals("wheel")) return "车轮赛";
        if (value.equals("doubles")) return "双打";
        if (value.equals("team")) return "团队赛";
        return "正规赛";
    }

    private String modeFromDisplayName(String value) {
        if (value.equals("娱乐赛")) return "entertainment";
        if (value.equals("车轮赛")) return "wheel";
        if (value.equals("双打")) return "doubles";
        if (value.equals("团队赛")) return "team";
        return "regular";
    }

    private List<String> savedPresetNames() {
        List<String> names = new ArrayList<>();
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith("preset_") && key.endsWith("_mode")) {
                String name = key.substring("preset_".length(), key.length() - "_mode".length());
                if (!name.isEmpty() && !names.contains(name)) names.add(name);
            }
        }
        return names;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
