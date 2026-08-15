# 车轮赛选手列表选择 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将车轮赛下一场 PK 的两个 Spinner 替换为可选中、可取消、最多选择两人的竖向选手列表。

**Architecture:** 使用无 Android 依赖的 `WheelRaceSelection` 管理选中索引，`MainActivity.showWheelPairChooser()` 只负责把状态映射成弹窗 UI，并复用现有车轮赛启动与等待流程。普通和选中 item 使用独立 drawable，选中态通过蓝色 2dp 边框表达。

**Tech Stack:** Java、Android Views、AppCompat `AlertDialog`、JUnit 4、Gradle Android plugin。

## Global Constraints

- 选中两个 item 后，“开始本轮 PK”才可用。
- 再次点击已选 item 必须取消选择。
- 同时最多选择两名选手，第三项点击不改变现有选择并提示用户。
- 保留“稍后安排”，不改变车轮赛会话生命周期、选手池保存格式、比赛历史和比赛引擎。
- 车轮赛启动时继续使用 BO1、11 分、每 2 分换发。
- AI 代码只在 `ai` 分支开发，不修改 `develop` 分支或未相关的用户改动。

---

### Task 1: Add and test pure selection state

**Files:**
- Create: `app/src/main/java/com/comm/pingscore/WheelRaceSelection.java`
- Test: `app/src/test/java/com/comm/pingscore/WheelRaceSelectionTest.java`

**Interfaces:**
- Produces `WheelRaceSelection(int maxSelections)`.
- Produces `boolean toggle(int index, int itemCount)`; returns `true` when the selection changed and `false` for an invalid index or a third selection.
- Produces `boolean isSelected(int index)`, `boolean isComplete()`, `int size()`, and `List<Integer> getSelectedIndexes()`.

- [x] **Step 1: Write the failing test**

  Cover selecting the first item, selecting a second item in order, clicking a selected item to remove it, rejecting a third item without changing the first two, and rejecting out-of-range indexes.

- [x] **Step 2: Run the focused test and verify it fails**

  Run:

  ```powershell
  $env:GRADLE_USER_HOME='E:\Android\.gradle'
  & 'E:\Android\.gradle\wrapper\dists\gradle-9.3.1-bin\23ovyewtku6u96viwx3xl3oks\gradle-9.3.1\bin\gradle.bat' :app:testDebugUnitTest --tests com.comm.pingscore.WheelRaceSelectionTest --offline --no-daemon
  ```

  Expected: compilation fails because `WheelRaceSelection` does not exist.

- [x] **Step 3: Implement the minimal selection object**

  Store selected indexes in insertion order. `toggle` removes an already-selected valid index, adds a new valid index only while the configured maximum is not reached, and otherwise returns `false`. Return a defensive read-only copy from `getSelectedIndexes()`.

- [x] **Step 4: Run the focused test and verify it passes**

  Run the same Gradle command and expect all `WheelRaceSelectionTest` methods to pass.

- [x] **Step 5: Commit the selection state**

  ```powershell
  git add app/src/main/java/com/comm/pingscore/WheelRaceSelection.java app/src/test/java/com/comm/pingscore/WheelRaceSelectionTest.java
  git commit -m "feat(wheel): add pair selection state"
  ```

### Task 2: Add wheel player item visuals

**Files:**
- Create: `app/src/main/res/drawable/bg_wheel_player.xml`
- Create: `app/src/main/res/drawable/bg_wheel_player_selected.xml`

**Interfaces:**
- `bg_wheel_player.xml` is the normal white surface with the existing border color and 8dp corner radius.
- `bg_wheel_player_selected.xml` is the same surface with a 2dp `score_blue` stroke and 8dp corner radius.

- [x] **Step 1: Add the two shape drawables**

  Keep the drawable backgrounds opaque and use only existing colors: `score_surface`, `score_border`, and `score_blue`.

- [x] **Step 2: Run resource compilation**

  Run `:app:assembleDebug --offline --no-daemon` and verify Android resource compilation succeeds.

### Task 3: Replace the wheel pair chooser UI

**Files:**
- Modify: `app/src/main/java/com/comm/pingscore/MainActivity.java` in `showWheelPairChooser()` and adjacent private UI helpers.

**Interfaces:**
- `showWheelPairChooser()` consumes normalized `wheelPlayers` and `WheelRaceSelection(2)`.
- It produces a vertical list of clickable rows, a selection summary, BO1/11-point rule rows, and the existing dialog actions.

- [x] **Step 1: Build the vertical chooser content**

  Keep the existing pool validation and active-match guard. Replace both Spinner controls with a summary block, two compact rule items (`本局规则 / BO1`, `单局分数 / 11 分`), and one player row per normalized name.

- [x] **Step 2: Add row rendering and toggle behavior**

  Each row must show the player initial, player name, and `可选` or `已选`. Store the row and status views so one refresh method can apply the normal/selected drawable, status color, and summary text after every toggle. A third selection must call the existing `toast` helper with `最多选择 2 名选手`.

- [x] **Step 3: Gate the start action**

  After `showStyledDialog`, disable the positive button until `selection.isComplete()` is true. On click, read `getSelectedIndexes()` in insertion order, assign `playerOne` and `playerTwo`, create the existing BO1 `MatchEngine`, persist, dismiss, and render. Keep the negative action calling `prepareWheelWaitingState()`.

- [x] **Step 4: Build the app**

  Run `:app:assembleDebug --offline --no-daemon` and fix Java/resource errors without changing unrelated modules.

### Task 4: Full verification and device check

**Files:**
- No new production files; verify the files from Tasks 1-3.

- [x] **Step 1: Run unit tests and build**

  ```powershell
  $env:GRADLE_USER_HOME='E:\Android\.gradle'
  & 'E:\Android\.gradle\wrapper\dists\gradle-9.3.1-bin\23ovyewtku6u96viwx3xl3oks\gradle-9.3.1\bin\gradle.bat' :app:testDebugUnitTest :app:assembleDebug --offline --no-daemon
  ```

  Expected: `BUILD SUCCESSFUL`.

- [x] **Step 2: Install the debug APK on the connected device**

  ```powershell
  & 'E:\Android\AndroidSDK\platform-tools\adb.exe' install -r 'app\build\outputs\apk\debug\app-debug.apk'
  ```

- [x] **Step 3: Verify the chooser manually**

  Open the wheel-race waiting state and verify: all players appear in one vertical list; tapping one item gives a blue border and `已选`; tapping it again restores `可选`; a second item can be selected; a third item is rejected; the start button is disabled before two selections and starts the next BO1 PK after two selections.

- [x] **Step 4: Check the worktree**

  Run `git status --short --branch` and confirm only the intended implementation files are staged or committed; preserve all unrelated user changes.
