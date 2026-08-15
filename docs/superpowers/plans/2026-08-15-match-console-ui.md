# PingScore 赛事控制台 UI 重构 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild PingScore's portrait, landscape, dialog, and history UI around the approved A “赛事控制台” direction while preserving current match behavior.

**Architecture:** Keep the existing Java `MainActivity`, `ScoreboardFragment`, `SetTabAdapter`, and `MatchEngine` boundaries. Consolidate presentation changes in XML drawables/layouts and a narrowly scoped `MatchUiStyler` Java helper for dynamically created dialog controls; `MainActivity` continues to own business state and event handling.

**Tech Stack:** Java 11, Android Views/XML, AppCompat, Material 3, RecyclerView, existing JVM tests, ADB screenshot verification.

## Global Constraints

- Work only on the `ai` branch; do not touch `develop`.
- Preserve match logic, persistence keys, and P0/P1/P2 navigation behavior.
- Use player 1 red, player 2 blue, serve green, settings dark, and pause red.
- Use `8dp` corners, `16dp` portrait margins, `8dp` or `12dp` gaps, and `44dp` minimum hit targets.
- Keep BO1, BO3, BO5, and BO7 tabs data-driven through `SetTabAdapter`.
- Verify portrait and landscape on the physical device before completion.

---

### Task 1: Establish shared visual resources

**Files:**
- Modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/res/values-night/themes.xml`
- Modify: `app/src/main/res/drawable/bg_score_card.xml`
- Modify: `app/src/main/res/drawable/bg_bottom_bar_blue.xml`
- Modify: `app/src/main/res/drawable/bg_icon_button.xml`
- Modify: `app/src/main/res/drawable/bg_minus_button.xml`
- Modify: `app/src/main/res/drawable/bg_pause_red.xml`
- Modify: `app/src/main/res/drawable/bg_plus_blue.xml`
- Modify: `app/src/main/res/drawable/bg_plus_red.xml`
- Modify: `app/src/main/res/drawable/bg_round_pill.xml`
- Modify: `app/src/main/res/drawable/bg_serve_active.xml`
- Modify: `app/src/main/res/drawable/bg_serve_green.xml`
- Modify: `app/src/main/res/drawable/bg_set_current.xml`
- Modify: `app/src/main/res/drawable/bg_set_selected.xml`
- Modify: `app/src/main/res/drawable/bg_set_tab.xml`
- Modify: `app/src/main/res/drawable/bg_settings_black.xml`
- Modify: `app/src/main/res/drawable/bg_start_button.xml`
- Modify: `app/src/main/res/drawable/bg_dialog.xml`
- Modify: `app/src/main/res/drawable/bg_dialog_section.xml`
- Modify: `app/src/main/res/drawable/bg_input_item.xml`
- Modify: `app/src/main/res/drawable/bg_pause_timer.xml`
- Create: `app/src/main/res/drawable/bg_score_card_red.xml`
- Create: `app/src/main/res/drawable/bg_score_card_blue.xml`
- Create: `app/src/main/res/drawable/bg_control_rail.xml`
- Create: `app/src/main/res/drawable/bg_set_completed_selected.xml`

**Interfaces:**
- Consumes: existing drawable names referenced by layouts and `MainActivity`.
- Produces: semantic resources used by portrait, landscape, and dialogs.

- [ ] **Step 1: Record the current visual baseline**

Run:

```powershell
& 'E:\Android\AndroidSDK\platform-tools\adb.exe' exec-out screencap -p > build\ui-before-console.png
```

Expected: a portrait screenshot showing the current match state before resource changes.

- [ ] **Step 2: Define semantic colors and theme attributes**

Add the following colors without removing existing identifiers until all callers are updated:

```xml
<color name="score_surface">#FFFFFFFF</color>
<color name="score_surface_soft">#FFEEF3F7</color>
<color name="score_border">#FFD5DEE6</color>
<color name="score_ink">#FF17212B</color>
<color name="score_muted">#FF6F7B87</color>
<color name="score_red">#FFEB565D</color>
<color name="score_blue">#FF2675D8</color>
<color name="score_green">#FF1FAC71</color>
<color name="score_settings">#FF1B2732</color>
<color name="score_pause">#FFD94D55</color>
```

Set Material primary to `@color/score_blue`, surface to `@color/score_surface`, and the window background to `@color/score_bg` in both theme resources.

- [ ] **Step 3: Normalize shape geometry**

Use an `8dp` radius and a `1dp @color/score_border` stroke for neutral surfaces. Create red and blue score cards with a `4dp` colored top stripe and a white body. Keep `bg_serve_active.xml` as a green chip. Change `bg_bottom_bar_blue.xml` into a neutral grouping container.

- [ ] **Step 4: Build resource changes**

Run:

```powershell
$env:GRADLE_USER_HOME='E:\Android\.gradle'
& 'E:\Android\.gradle\wrapper\dists\gradle-9.3.1-bin\23ovyewtku6u96viwx3xl3oks\gradle-9.3.1\bin\gradle.bat' :app:assembleDebug --offline --no-daemon
```

Expected: `BUILD SUCCESSFUL` with no missing resource references.

- [ ] **Step 5: Commit the resource foundation**

```powershell
git add app/src/main/res/values app/src/main/res/drawable
git commit -m "feat(ui): establish match console visual tokens"
```

### Task 2: Rebuild portrait hierarchy and set tabs

**Files:**
- Modify: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/res/layout/fragment_scoreboard.xml`
- Modify: `app/src/main/res/layout/item_set_tab.xml`
- Modify: `app/src/main/java/com/comm/pingscore/SetTabAdapter.java`
- Modify: `app/src/main/java/com/comm/pingscore/MainActivity.java:303-410`
- Test: `app/src/androidTest/java/com/comm/pingscore/SetTabAdapterUiTest.java`

**Interfaces:**
- Consumes: Task 1 colors and drawables; existing IDs referenced by `MainActivity`.
- Produces: portrait rendering for current, completed, pending, paused, and adjustment states.

- [ ] **Step 1: Write the tab rendering test**

Inflate `item_set_tab`, bind the three adapter states, and assert exact text plus status-specific styling:

```java
assertEquals("第 2 局\n0:0", currentHolder.label.getText().toString());
assertEquals("第 3 局\n待赛", pendingHolder.label.getText().toString());
assertTrue(currentHolder.label.getBackground().getConstantState()
        .equals(context.getDrawable(R.drawable.bg_set_current).getConstantState()));
```

- [ ] **Step 2: Run the test to verify the baseline**

Run:

```powershell
$env:GRADLE_USER_HOME='E:\Android\.gradle'
& 'E:\Android\.gradle\wrapper\dists\gradle-9.3.1-bin\23ovyewtku6u96viwx3xl3oks\gradle-9.3.1\bin\gradle.bat' :app:connectedDebugAndroidTest --offline --no-daemon
```

Expected: current text behavior is confirmed; adjust the test only if drawable constant-state comparison is device-specific.

- [ ] **Step 3: Implement the portrait layout**

In `activity_main.xml`, retain all IDs but make the header `52dp`, the state strip `56dp`, and the tab row `48dp` with a `12dp` gap below the state strip. In `fragment_scoreboard.xml`, create two equal player columns separated by `12dp`, red/blue score cards, `56dp` plus buttons, `44dp` minus buttons, and an `8dp`-gapped control row without a blue backing panel.

- [ ] **Step 4: Implement tab and disabled-control styling**

Update `SetTabAdapter` to map completed selected items to `bg_set_completed_selected`, current items to `bg_set_current`, and pending items to `bg_set_tab`. In `MainActivity.setScoreControlsEnabled`, keep visibility behavior but call:

```java
scoreboardRoot.findViewById(R.id.player_one_plus).setAlpha(enabled ? 1f : 0.48f);
scoreboardRoot.findViewById(R.id.player_two_plus).setAlpha(enabled ? 1f : 0.48f);
```

Apply the same alpha rule to both minus buttons.

- [ ] **Step 5: Build, install, and inspect portrait**

Run:

```powershell
$env:GRADLE_USER_HOME='E:\Android\.gradle'
& 'E:\Android\.gradle\wrapper\dists\gradle-9.3.1-bin\23ovyewtku6u96viwx3xl3oks\gradle-9.3.1\bin\gradle.bat' :app:testDebugUnitTest :app:assembleDebug --offline --no-daemon
& 'E:\Android\AndroidSDK\platform-tools\adb.exe' install -r app\build\outputs\apk\debug\app-debug.apk
```

Expected: no text overlap, visible side margins, and no clipped bottom control.

- [ ] **Step 6: Commit portrait UI**

```powershell
git add app/src/main/res/layout/activity_main.xml app/src/main/res/layout/fragment_scoreboard.xml app/src/main/res/layout/item_set_tab.xml app/src/main/java/com/comm/pingscore/SetTabAdapter.java app/src/main/java/com/comm/pingscore/MainActivity.java app/src/androidTest
git commit -m "feat(ui): rebuild portrait match console"
```

### Task 3: Recompose the landscape control desk

**Files:**
- Modify: `app/src/main/res/layout-land/activity_main.xml`
- Modify: `app/src/main/res/layout-land/fragment_scoreboard.xml`
- Modify: `app/src/main/res/layout-land/item_set_tab.xml`
- Modify: `app/src/main/java/com/comm/pingscore/MainActivity.java:165-190`

**Interfaces:**
- Consumes: shared resources from Task 1 and existing landscape IDs.
- Produces: a left control rail and a dedicated right-side scoring stage with unchanged listeners.

- [ ] **Step 1: Implement the rail and stage**

Make `land_control_rail` a `224dp` white bordered surface with `12dp` inner padding and a `16dp` gap before the fragment container. Remove the blue action-panel fill. Stack dark settings, red pause, and green serve as three `44dp` controls with `8dp` spacing. Keep the set RecyclerView vertical and scrollable.

- [ ] **Step 2: Implement landscape score proportions**

Give the landscape fragment a `12dp` outer margin. Reuse red and blue score-card resources; keep pause at the top. Hide the fragment bottom bar because actions remain in the rail. Use a large score text size that leaves player names and all four score controls readable.

- [ ] **Step 3: Verify rotation and IDs**

Run:

```powershell
& 'E:\Android\AndroidSDK\platform-tools\adb.exe' shell settings put system accelerometer_rotation 0
& 'E:\Android\AndroidSDK\platform-tools\adb.exe' shell settings put system user_rotation 1
& 'E:\Android\AndroidSDK\platform-tools\adb.exe' exec-out screencap -p > build\ui-landscape-console.png
```

Expected: no overlap between rail and scoreboard; all three action controls are visible.

- [ ] **Step 4: Commit landscape UI**

```powershell
git add app/src/main/res/layout-land app/src/main/java/com/comm/pingscore/MainActivity.java
git commit -m "feat(ui): compose landscape match control desk"
```

### Task 4: Standardize setup, pause, history, and results

**Files:**
- Create: `app/src/main/java/com/comm/pingscore/MatchUiStyler.java`
- Modify: `app/src/main/java/com/comm/pingscore/MainActivity.java:547-706`
- Modify: `app/src/main/java/com/comm/pingscore/MainActivity.java:731-984`
- Modify: `app/src/main/java/com/comm/pingscore/MainActivity.java:1402-1933`
- Modify: `app/src/main/java/com/comm/pingscore/MainActivity.java:1935-2188`
- Test: `app/src/test/java/com/comm/pingscore/MatchUiStylerTest.java`

**Interfaces:**
- Consumes: Android `Context`, semantic resources, and existing `MainActivity.dp(int)`.
- Produces: `MatchUiStyler.historySubtitle(String, int, int)`, `MatchUiStyler.resultLabel(int, int, int, String)`, `styleInput(EditText)`, `styleSpinner(Spinner)`, and `styleDialog(AlertDialog, Context, int)`.

- [ ] **Step 1: Write failing pure formatting tests**

```java
assertEquals("正规赛 · BO3 · 3 局",
        MatchUiStyler.historySubtitle("regular", 3, 3));
assertEquals("第 2 局   11:9   玩家 1 胜",
        MatchUiStyler.resultLabel(2, 11, 9, "玩家 1"));
```

- [ ] **Step 2: Run the test and confirm it fails**

Run:

```powershell
$env:GRADLE_USER_HOME='E:\Android\.gradle'
& 'E:\Android\.gradle\wrapper\dists\gradle-9.3.1-bin\23ovyewtku6u96viwx3xl3oks\gradle-9.3.1\bin\gradle.bat' :app:testDebugUnitTest --offline --no-daemon
```

Expected: compilation fails because `MatchUiStyler` is not defined.

- [ ] **Step 3: Implement the presentation helper**

Create the helper with the two tested pure functions and static styling functions. Apply `bg_input_item` to dynamic `EditText` and `Spinner` fields, `bg_dialog_section` to configuration sections, and full-width card rows for player and team additions. Do not change validation or SharedPreferences keys.

- [ ] **Step 4: Replace default history rows**

In `showHistory()`, build a scrollable white-card list from current summaries. Each card calls `showHistoryDetail(entry)`. In `showHistoryDetail()`, render a mode subtitle and one independent result row per set using `MatchUiStyler.resultLabel(...)`. Continue to use current history persistence.

- [ ] **Step 5: Apply the common dialog surface**

Wire the helper through `showStyledDialog`, `showModernMatchSetup`, `showModernPauseChooser`, `showPauseLockedDialog`, `showServeChooser`, `showDoublesServeChooser`, team-pair dialogs, `finishMatch`, `showScoreAdjustDialog`, and `showGlobalSettings`. Preserve every action label, click listener, pause timer, and team queue rule.

- [ ] **Step 6: Run dialog verification**

Run:

```powershell
$env:GRADLE_USER_HOME='E:\Android\.gradle'
& 'E:\Android\.gradle\wrapper\dists\gradle-9.3.1-bin\23ovyewtku6u96viwx3xl3oks\gradle-9.3.1\bin\gradle.bat' :app:testDebugUnitTest :app:assembleDebug --offline --no-daemon
& 'E:\Android\AndroidSDK\platform-tools\adb.exe' install -r app\build\outputs\apk\debug\app-debug.apk
```

Expected: all tests pass, all five match modes still open, pause counts down once per second, and history/detail dialogs are navigable.

- [ ] **Step 7: Commit dialog UI**

```powershell
git add app/src/main/java/com/comm/pingscore/MatchUiStyler.java app/src/main/java/com/comm/pingscore/MainActivity.java app/src/test/java/com/comm/pingscore app/src/main/res/drawable
git commit -m "feat(ui): unify setup and match dialogs"
```

### Task 5: Run visual acceptance

**Files:**
- Modify: `docs/需求文档.md`
- Create: `build/ui-portrait-console.png`
- Create: `build/ui-landscape-console.png`
- Create: `build/ui-pause-console.png`
- Create: `build/ui-setup-console.png`

**Interfaces:**
- Consumes: debug APK and the connected Android device.
- Produces: screenshot evidence and a documented visual acceptance checklist.

- [ ] **Step 1: Exercise the state matrix**

Capture portrait live regular BO3, completed selected set with `赛果调整`, pause lock at roughly `00:59`, match setup with one added player row, and landscape live match.

- [ ] **Step 2: Inspect screenshots against the specification**

Confirm a visible `16dp` portrait margin or `12dp` landscape stage margin, no text overlap, no thick black score-card border, correct semantic colors, and no button clipped by system navigation.

- [ ] **Step 3: Run the final build**

Run:

```powershell
$env:GRADLE_USER_HOME='E:\Android\.gradle'
& 'E:\Android\.gradle\wrapper\dists\gradle-9.3.1-bin\23ovyewtku6u96viwx3xl3oks\gradle-9.3.1\bin\gradle.bat' :app:testDebugUnitTest :app:assembleDebug --offline --no-daemon
```

Expected: `BUILD SUCCESSFUL` and all unit tests pass.

- [ ] **Step 4: Record the implemented rules**

Add a “赛事控制台 UI 规范” section to `docs/需求文档.md` containing the color semantics, portrait/landscape layout rules, and the acceptance criteria from this plan.

- [ ] **Step 5: Commit final acceptance**

```powershell
git add docs/需求文档.md
git commit -m "docs: record match console ui acceptance"
```

## Self-Review

### Spec coverage

- Task 1 covers color, shape, and theme consistency.
- Task 2 covers portrait hierarchy, data-driven BO tabs, completed-set adjustment, and disabled score controls.
- Task 3 covers a separated landscape rail and score stage.
- Task 4 covers setup, pause, serve, teams, history, results, and global settings.
- Task 5 covers real-device screenshots, overlap checks, final build, and product documentation.

### Placeholder scan

The scan found no prohibited planning placeholders. Every task includes named files, concrete resource or method work, and a verification command.

### Type consistency

`MatchUiStyler` is the only new Java type. Its methods are defined in Task 4 before its use in `MainActivity`. Existing `MainActivity` handlers and `SetTabAdapter` stay the behavior boundaries, so no scoring interface changes are introduced.
