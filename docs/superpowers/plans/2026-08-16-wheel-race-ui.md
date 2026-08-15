# PingScore 车轮赛 UI 与流程 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将车轮赛从“预生成对阵列表 + 结束即退出当前比赛”调整为可持续的车轮赛会话：用户每次从选手池中选择两名选手开始一场独立 BO1，结束后可继续选择、稍后安排或明确结束车轮赛；主界面在待赛状态持续提供入口，并在横竖屏保持现有布局 ID 与可点击区域。

**Architecture:** 保留 `MainActivity` 作为当前比赛 UI 与 Android 持久化协调者，新增一个无 Android 依赖的 `WheelRacePolicy` 负责选手池和配对校验。车轮赛会话状态与当前 PK 状态分开保存：`KEY_WHEEL_SESSION_ACTIVE` 表示车轮赛尚未明确结束，`KEY_ACTIVE` 只表示当前这场 PK 是否正在进行。配对弹窗改为两个有样式的 Spinner 选择项，不再预生成固定顺序的 pair 列表；结束弹窗根据模式分支，只有“结束车轮赛”才清除会话。

**Tech Stack:** Java 11, Android Views/XML, existing `MainActivity`, `MatchEngine`, `MatchUiStyler`, SharedPreferences, JUnit 4.

## Global Constraints

- 只在 `ai` 分支开发；不切换、不修改、不合并 `develop`。
- 保留工作区中用户已有的无关修改，不执行 reset、checkout、rebase 或清理命令。
- 车轮赛默认 BO1、11 分、每 2 分换发；同一对选手之后允许再次 PK，不强制顺序、不限制每人场数。
- “待赛 + 选择下一场 PK”是车轮赛会话级入口；当前 PK 进行中时入口不可启动第二场，并给出明确提示。
- 车轮赛历史记录仍按每场 PK 追加；结束当前 PK 不得清除选手池或会话，显式结束车轮赛不删除历史。
- 复用已有 `MatchUiStyler.styleSpinner`、`dialogFormParams` 和现有布局 ID，避免引入新的页面栈或无必要的 XML 改动。
- 每个实现任务先写/更新可执行测试，再实现代码；完成前必须运行单元测试、Debug 构建，并检查代码中不存在仍会调用 `clearActive()` 结束车轮赛的路径。

---

### Task 1: Add pure wheel-race selection rules with tests

**Files:**
- Create: `app/src/main/java/com/comm/pingscore/WheelRacePolicy.java`
- Create: `app/src/test/java/com/comm/pingscore/WheelRacePolicyTest.java`

**Interfaces:**
- Produces `WheelRacePolicy.hasValidPool(List<String>)` for session restoration/setup validation.
- Produces `WheelRacePolicy.canStartPair(List<String>, int, int)` for two-spinner validation.
- Produces `WheelRacePolicy.normalizePool(Collection<String>)` so blank names and duplicates cannot create invalid selectable entries.

- [ ] **Step 1: Write the failing unit tests**

Add tests covering:

```java
assertTrue(WheelRacePolicy.hasValidPool(Arrays.asList("甲", "乙")));
assertFalse(WheelRacePolicy.hasValidPool(Arrays.asList("甲", "甲")));
assertFalse(WheelRacePolicy.canStartPair(Arrays.asList("甲", "乙"), 0, 0));
assertTrue(WheelRacePolicy.canStartPair(Arrays.asList("甲", "乙"), 1, 0));
assertEquals(Arrays.asList("甲", "乙", "丙"),
        WheelRacePolicy.normalizePool(Arrays.asList(" 甲 ", "乙", "甲", "", "丙")));
```

- [ ] **Step 2: Run the focused test and confirm the baseline failure**

Run:

```powershell
$env:GRADLE_USER_HOME='E:\Android\.gradle'
& 'E:\Android\.gradle\wrapper\dists\gradle-9.3.1-bin\23ovyewtku6u96viwx3xl3oks\gradle-9.3.1\bin\gradle.bat' :app:testDebugUnitTest --tests com.comm.pingscore.WheelRacePolicyTest --offline --no-daemon
```

Expected: compilation fails because `WheelRacePolicy` does not exist yet.

- [ ] **Step 3: Implement the policy class**

Use a final utility class with no Android imports. `normalizePool` trims, removes blank values, preserves first-seen order, and removes duplicates. `hasValidPool` requires at least two distinct nonblank values. `canStartPair` requires a valid pool, two in-range indices, and different selected values.

- [ ] **Step 4: Run the focused test and the existing engine tests**

```powershell
$env:GRADLE_USER_HOME='E:\Android\.gradle'
& 'E:\Android\.gradle\wrapper\dists\gradle-9.3.1-bin\23ovyewtku6u96viwx3xl3oks\gradle-9.3.1\bin\gradle.bat' :app:testDebugUnitTest --tests com.comm.pingscore.WheelRacePolicyTest --tests com.comm.pingscore.MatchEngineTest --offline --no-daemon
```

Expected: all selected tests pass.

### Task 2: Separate wheel session persistence from current PK persistence

**Files:**
- Modify: `app/src/main/java/com/comm/pingscore/MainActivity.java` near preference constants/fields, setup callbacks, `persist`, `loadSavedMatch`, and `clearActive`.

**Interfaces:**
- Adds `KEY_WHEEL_SESSION_ACTIVE` and an in-memory `wheelSessionActive` flag.
- Adds `prepareWheelWaitingState()`, `finishWheelSession()`, `isWheelSessionWaiting()`, and `isWheelMatchActive()` helpers used by rendering and click handlers.

- [ ] **Step 1: Initialize the separate session state**

Load `wheel_players` whenever saved mode is `wheel`, not only when `KEY_ACTIVE` is true. Read `KEY_WHEEL_SESSION_ACTIVE`; for older installs with no such key, treat a valid saved wheel player pool as an active waiting session when the saved mode is `wheel`, then persist the new key.

- [ ] **Step 2: Update wheel setup entry points**

In both legacy and modern setup flows, normalize the submitted player pool through `WheelRacePolicy.normalizePool`, require a valid pool, set `mode = "wheel"`, set `wheelSessionActive = true`, persist the player pool and session flag, create a fresh BO1 engine, and open the pair chooser. Do not start a PK until the user confirms both selected players.

- [ ] **Step 3: Preserve session state in `persist()`**

Keep `KEY_ACTIVE` equal to `engine.isStarted() && !engine.isFinished()` for the current PK. Add `KEY_WHEEL_SESSION_ACTIVE` from `wheelSessionActive` so finishing a PK cannot implicitly end the wheel session. Keep current player names and records unchanged for resume/history compatibility.

- [ ] **Step 4: Make waiting and explicit-end transitions explicit**

`prepareWheelWaitingState()` creates an unstarted BO1 engine, resets pause/tabs, sets the displayed players to `待选择`, clears only current-PK state, keeps the player pool, and persists the active wheel session. `finishWheelSession()` sets the session flag false, clears current active state and pause callbacks, creates a fresh wheel BO1 engine, and renders the normal start state without deleting history or the saved pool.

- [ ] **Step 5: Restore both states safely**

When `KEY_ACTIVE` is false but `wheelSessionActive` is true, restore an unstarted BO1 waiting state instead of a misleading resumable match. When both are true, restore the current PK exactly as before, including scores, set records, server, and pause timer.

### Task 3: Replace the wheel pair list with a styled two-player chooser

**Files:**
- Modify: `app/src/main/java/com/comm/pingscore/MainActivity.java` in `bindActions`, `render`, and `showWheelPairChooser`.

**Interfaces:**
- The bottom/main `team_queue_button` remains the existing shared view ID but becomes visible for `mode.equals("wheel")`.
- `showWheelPairChooser()` presents two styled selectable items, validates distinct players, and starts one BO1 PK only after confirmation.

- [ ] **Step 1: Add the failing interaction-level assertions to the implementation checklist**

Before changing production code, document the manual checks in the task log: a pool of four players must show two independently selectable controls; choosing the same player in both controls must keep the dialog open; choosing two different players must start a BO1 and set `KEY_ACTIVE` true while retaining `KEY_WHEEL_SESSION_ACTIVE` true.

- [ ] **Step 2: Build the chooser content**

Replace the generated `i < j` pair array with a vertically spaced dialog content containing a short explanation, two labels (`选手 1`, `选手 2`), and two `Spinner`s styled through `MatchUiStyler.styleSpinner`. Both spinners contain the full normalized player pool so the user is not constrained by fixed pair order.

- [ ] **Step 3: Wire validation and start**

Use a custom positive-button listener. If `WheelRacePolicy.canStartPair` is false, show an inline error/toast and leave the chooser open. Otherwise set `playerOne`/`playerTwo`, set BO1/11/2, set `wheelSessionActive = true`, create and start the engine, call `persist()`, dismiss the chooser, and render. The negative action is `稍后安排` and only returns to the waiting state.

- [ ] **Step 4: Update main-page entry behavior**

Route the existing start button and bottom waiting entry to `showWheelPairChooser()` when the wheel session is active and there is no current PK. When a wheel PK is active, disable the waiting entry and show a concise “当前 PK 进行中，结束后再安排下一场” message if it is tapped. For non-wheel modes, preserve current setup/team behavior.

- [ ] **Step 5: Render a truthful waiting state**

When a wheel session is waiting, show `车轮赛 · 待选择下一场 PK`, use `选择下一场 PK` for the primary start action, show `待选择` in the two player name slots, hide score adjustment/score controls, and set the waiting entry text to `待赛  + 选择下一场 PK`. When a PK is active, restore the normal player names and scoring controls.

### Task 4: Keep the wheel session alive after a PK and expose explicit end

**Files:**
- Modify: `app/src/main/java/com/comm/pingscore/MainActivity.java` in `finishMatch()` and related result-dialog code.

**Interfaces:**
- Wheel result dialog actions: `稍后安排`, `结束车轮赛`, `选择下一场 PK`.
- Regular, entertainment, doubles, and existing team result flows retain their current actions.

- [ ] **Step 1: Separate the result-dialog branch by mode**

Keep the existing color-coded score/result content. For wheel mode, do not attach `clearActive()` to the default negative action and do not call `clearActive()` before showing the next-pair chooser.

- [ ] **Step 2: Implement wheel result actions**

`选择下一场 PK` dismisses the result dialog, calls `prepareWheelWaitingState()`, and opens `showWheelPairChooser()`. `稍后安排` dismisses the result dialog, calls `prepareWheelWaitingState()`, and leaves the main waiting entry visible. `结束车轮赛` dismisses the dialog and calls `finishWheelSession()`.

- [ ] **Step 3: Preserve per-PK history**

Append the finished PK to history before any transition. Confirm that choosing another pair creates a new independent BO1 record while the previous set scores remain in the history list and no pending pair is silently deleted.

- [ ] **Step 4: Audit all wheel exit paths**

Search for `mode.equals("wheel")` and every `clearActive()` call. Confirm only the explicit end action clears `wheelSessionActive`; setup reset may clear the current PK but must not accidentally erase the player pool during a waiting session.

### Task 5: Verify UI, persistence, and regression behavior

**Files:**
- Modify only files already listed above if verification finds a concrete issue.
- Evidence: `app/build/outputs/apk/debug/app-debug.apk` and device screenshots/logs as available.

- [ ] **Step 1: Run all JVM tests and build Debug**

```powershell
$env:GRADLE_USER_HOME='E:\Android\.gradle'
& 'E:\Android\.gradle\wrapper\dists\gradle-9.3.1-bin\23ovyewtku6u96viwx3xl3oks\gradle-9.3.1\bin\gradle.bat' :app:testDebugUnitTest :app:assembleDebug --offline --no-daemon
```

Expected: `BUILD SUCCESSFUL` and all unit tests pass.

- [ ] **Step 2: Install and exercise the wheel flow**

```powershell
& 'E:\Android\AndroidSDK\platform-tools\adb.exe' install -r app\build\outputs\apk\debug\app-debug.apk
```

Exercise: create four players; select players 1/3; finish the BO1; verify the result dialog offers all three wheel actions; choose `稍后安排`; verify the waiting entry remains; choose a different pair; finish again; relaunch; verify the session restores as waiting if no PK is active; choose `结束车轮赛`; verify the normal start state appears and history remains.

- [ ] **Step 3: Check portrait and landscape screenshots**

Capture both orientations and inspect that the waiting entry, two-player chooser, and result dialog have no clipped text or overlapping controls. Confirm the existing vertical landscape rail remains intact.

- [ ] **Step 4: Run final source audit**

Use `rg` to verify the new key and helpers are used consistently, no generated pair-list code remains, and no wheel result action invokes `clearActive()` implicitly.

- [ ] **Step 5: Commit the focused implementation**

```powershell
git add app/src/main/java/com/comm/pingscore/WheelRacePolicy.java app/src/test/java/com/comm/pingscore/WheelRacePolicyTest.java app/src/main/java/com/comm/pingscore/MainActivity.java docs/superpowers/plans/2026-08-16-wheel-race-ui.md
git commit -m "feat(wheel): keep race session open between pairings"
```

Do not stage the unrelated modified or untracked files listed by the existing worktree status.

## Self-Review Checklist

- [ ] All approved wheel-race requirements are mapped to a concrete task and file.
- [ ] Every production change has a focused test or an explicit device verification step.
- [ ] No task contains a placeholder such as TODO, TBD, or an unbounded “write tests” instruction.
- [ ] `KEY_ACTIVE` and `KEY_WHEEL_SESSION_ACTIVE` have distinct meanings in setup, restore, finish, and UI rendering.
- [ ] A user can choose any two distinct players repeatedly, including the same pair again later.
- [ ] The result dialog cannot end the session unless the user taps `结束车轮赛`.
- [ ] Portrait and landscape use existing IDs and maintain the current score-console layout contract.
