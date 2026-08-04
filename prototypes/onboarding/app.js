(() => {
  "use strict";

  const root = document.documentElement;
  const appMain = document.querySelector("#app-main");
  const syncScreen = document.querySelector('[data-screen="sync"]');
  const welcomeScreen = document.querySelector('[data-screen="welcome"]');
  const coverGridStage = document.querySelector("#cover-grid-stage");
  const coverGrid = document.querySelector("#cover-grid");
  const importCard = document.querySelector("#import-card");
  const importCardBottom = document.querySelector("#import-card-bottom");
  const syncHeadingBlock = document.querySelector(".sync-heading-block");
  const todayPreview = document.querySelector("#today-preview");
  const todayPreviewTopline = document.querySelector("#today-preview-topline");
  const todayArtwork = document.querySelector("#today-artwork");
  const previewCopy = document.querySelector("#preview-copy");
  const previewCaption = document.querySelector("#preview-caption");
  const previewAction = document.querySelector("#preview-action");
  const appToast = document.querySelector("#app-toast");
  const controlStatus = document.querySelector("#control-status");
  const controlButtons = [...document.querySelectorAll("[data-control]")];
  const actionButtons = [...document.querySelectorAll("[data-action]")];
  const providerActionButtons = [...document.querySelectorAll("[data-provider-action]")];
  const reducedMotionQuery = window.matchMedia("(prefers-reduced-motion: reduce)");

  const providers = {
    anilist: {
      label: "AniList",
      chip: "ANILIST",
      possessive: "AniList"
    },
    myanimelist: {
      label: "MyAnimeList",
      chip: "MYANIMELIST",
      possessive: "MyAnimeList"
    }
  };

  const motionLabels = {
    a: "A / Cover Deal",
    b: "B / Panel Wipe",
    c: "C / Mosaic Assembly"
  };

  const coverData = [
    { mark: "01", layout: "slash", main: "#267a85", secondary: "#174f59", accent: "#c8f53d", ink: "#0b3036" },
    { mark: "02", layout: "corner", main: "#3f78b1", secondary: "#214a77", accent: "#e7b84c", ink: "#18344f" },
    { mark: "03", layout: "stack", main: "#d86b63", secondary: "#8e3d45", accent: "#ffffff", ink: "#642a35" },
    { mark: "04", layout: "corner", main: "#9dbb3e", secondary: "#4c6a33", accent: "#0b0d0f", ink: "#354524" },
    { mark: "05", layout: "slash", main: "#226c91", secondary: "#143a61", accent: "#e8eef1", ink: "#173247" },
    { mark: "06", layout: "stack", main: "#c88b4e", secondary: "#7d4e35", accent: "#0b0d0f", ink: "#5e3928" },
    { mark: "07", layout: "corner", main: "#347d70", secondary: "#1d514b", accent: "#e26863", ink: "#19413e" },
    { mark: "08", layout: "slash", main: "#3b6da8", secondary: "#233f6a", accent: "#c8f53d", ink: "#1d3151" },
    { mark: "09", layout: "stack", main: "#cf665d", secondary: "#783f45", accent: "#f0d066", ink: "#5b2e39" },
    { mark: "10", layout: "corner", main: "#317d8b", secondary: "#1d4a5d", accent: "#f2f5f2", ink: "#173744" }
  ];

  const state = {
    variant: root.dataset.motion || "a",
    state: root.dataset.state || "welcome",
    provider: root.dataset.provider || "anilist",
    phase: "idle",
    reducedMotion: root.dataset.reducedMotion === "true",
    slowMotion: root.dataset.slowMotion === "true"
  };

  const activeAnimations = new Set();
  const managedTimers = new Set();
  const managedFrames = new Set();
  let importRun = 0;

  function isValidProvider(provider) {
    return Object.prototype.hasOwnProperty.call(providers, provider);
  }

  function isValidVariant(variant) {
    return Object.prototype.hasOwnProperty.call(motionLabels, variant);
  }

  function isValidState(nextState) {
    return ["welcome", "importing", "complete"].includes(nextState);
  }

  function currentProvider() {
    return providers[state.provider] || providers.anilist;
  }

  function shouldReduceMotion() {
    return state.reducedMotion || reducedMotionQuery.matches;
  }

  function motionScale() {
    return shouldReduceMotion() || !state.slowMotion ? 1 : 2;
  }

  function motionDuration(duration, options = {}) {
    if (shouldReduceMotion()) return options.reducedDuration ?? duration;
    return duration * (options.slow === false ? 1 : motionScale());
  }

  function motionDelay(delay, options = {}) {
    if (shouldReduceMotion()) return options.reducedDelay ?? 0;
    return (delay || 0) * (options.slow === false ? 1 : motionScale());
  }

  function schedule(callback, delay) {
    const timer = window.setTimeout(() => {
      managedTimers.delete(timer);
      callback();
    }, Math.max(0, delay));
    managedTimers.add(timer);
    return timer;
  }

  function scheduleMotion(callback, delay) {
    return schedule(callback, shouldReduceMotion() ? 0 : delay * motionScale());
  }

  function scheduleFrame(callback) {
    const frame = window.requestAnimationFrame((timestamp) => {
      managedFrames.delete(frame);
      callback(timestamp);
    });
    managedFrames.add(frame);
    return frame;
  }

  function trackAnimation(animation) {
    if (!animation) return;
    activeAnimations.add(animation);
    animation.finished
      .then(() => {
        if (!activeAnimations.has(animation)) return;
        if (typeof animation.commitStyles !== "function") return;
        try {
          animation.commitStyles();
          animation.cancel();
          activeAnimations.delete(animation);
        } catch (error) {
          // The animation remains tracked so shared cleanup can still cancel it.
        }
      })
      .catch(() => {});
  }

  function animateElement(element, keyframes, options = {}) {
    if (!element || typeof element.animate !== "function") return null;

    const duration = motionDuration(options.duration ?? 0, options);
    const delay = motionDelay(options.delay ?? 0, options);
    if (duration <= 0) return null;

    try {
      const animation = element.animate(keyframes, {
        duration,
        delay,
        easing: options.easing || "cubic-bezier(0.22, 1, 0.36, 1)",
        fill: "both"
      });
      trackAnimation(animation);
      return animation;
    } catch (error) {
      return null;
    }
  }

  function clearMotionInline(element) {
    if (!element) return;
    ["transform", "opacity", "clip-path", "will-change", "z-index", "left", "top", "width", "height"].forEach((property) => {
      element.style.removeProperty(property);
    });
  }

  function removeTransientMotionDom() {
    document.querySelectorAll(".assembly-stack, .assembly-flyer, .panel-tracking-line").forEach((element) => element.remove());
  }

  function cancelRunningAnimations() {
    managedTimers.forEach((timer) => window.clearTimeout(timer));
    managedTimers.clear();

    managedFrames.forEach((frame) => window.cancelAnimationFrame(frame));
    managedFrames.clear();

    activeAnimations.forEach((animation) => {
      try {
        animation.cancel();
      } catch (error) {
        // An already-finished animation can safely be ignored.
      }
    });
    activeAnimations.clear();

    removeTransientMotionDom();
    [coverGridStage, coverGrid, importCard, importCardBottom, syncHeadingBlock, todayPreview, todayPreviewTopline, todayArtwork, previewCopy, previewCaption].forEach((element) => {
      clearMotionInline(element);
    });

    coverGridStage.classList.remove("is-assembling", "is-compressing");
    coverGrid.classList.remove("is-dealing", "is-panel-wiping", "is-reduced-reveal");
    importCard.classList.remove("is-crossfading", "is-complete");
    todayPreview.classList.remove("is-entering");

    coverGrid.querySelectorAll(".cover-tile").forEach((tile) => {
      tile.classList.remove("is-revealed", "is-wiping");
      clearMotionInline(tile);
      clearMotionInline(tile.querySelector(".cover-art"));
    });

    todayArtwork.replaceChildren();
    appToast.hidden = true;
    appToast.textContent = "";

    [...controlButtons, ...actionButtons, ...providerActionButtons, previewAction].forEach((button) => {
      if (!button) return;
      button._keyboardActive = false;
      button._suppressClick = false;
      if (button._suppressClickTimer) window.clearTimeout(button._suppressClickTimer);
      button._suppressClickTimer = null;
    });
  }

  function setPressed(buttons, value) {
    buttons.forEach((button) => {
      const isActive = button.dataset.value === value;
      button.classList.toggle("is-active", isActive);
      button.setAttribute("aria-pressed", String(isActive));
    });
  }

  function updateControlStatus() {
    const suffix = [
      state.reducedMotion ? "Reduced motion" : "",
      state.slowMotion ? "0.5× slow" : ""
    ].filter(Boolean);
    const motionSuffix = suffix.length ? ` · ${suffix.join(" · ")}` : "";
    controlStatus.textContent = `${motionLabels[state.variant]} · ${state.state[0].toUpperCase()}${state.state.slice(1)} · ${currentProvider().label}${motionSuffix}`;
  }

  function updateControls() {
    setPressed(controlButtons.filter((button) => button.dataset.control === "variant"), state.variant);
    setPressed(controlButtons.filter((button) => button.dataset.control === "state"), state.state);
    setPressed(controlButtons.filter((button) => button.dataset.control === "provider"), state.provider);

    const reducedButton = actionButtons.find((button) => button.dataset.action === "reduced-motion");
    const slowButton = actionButtons.find((button) => button.dataset.action === "slow-motion");
    if (reducedButton) reducedButton.setAttribute("aria-pressed", String(state.reducedMotion));
    if (slowButton) slowButton.setAttribute("aria-pressed", String(state.slowMotion));

    root.dataset.motion = state.variant;
    root.dataset.state = state.state;
    root.dataset.provider = state.provider;
    root.dataset.reducedMotion = String(state.reducedMotion);
    root.dataset.slowMotion = String(state.slowMotion);
    updateControlStatus();
  }

  function createArtworkElement(cover, className) {
    const art = document.createElement("span");
    art.className = className;
    art.dataset.layout = cover.layout;
    art.style.setProperty("--cover-main", cover.main);
    art.style.setProperty("--cover-secondary", cover.secondary);
    art.style.setProperty("--cover-accent", cover.accent);
    art.style.setProperty("--cover-ink", cover.ink);

    const mark = document.createElement("span");
    mark.className = "cover-mark";
    mark.textContent = cover.mark;
    mark.setAttribute("aria-hidden", "true");
    art.append(mark);
    return art;
  }

  function createCoverTile(cover, index, isRevealed) {
    const tile = document.createElement("div");
    tile.className = `cover-tile${isRevealed ? " is-revealed" : ""}`;
    tile.dataset.index = String(index);
    tile.style.setProperty("--cover-main", cover.main);
    tile.style.setProperty("--cover-secondary", cover.secondary);
    tile.style.setProperty("--cover-accent", cover.accent);
    tile.style.setProperty("--cover-ink", cover.ink);
    tile.setAttribute("aria-hidden", "true");
    tile.append(createArtworkElement(cover, "cover-art"));
    return tile;
  }

  function renderCovers(isRevealed) {
    removeTransientMotionDom();
    coverGridStage.classList.remove("is-assembling", "is-compressing");
    coverGrid.className = "cover-grid";
    coverGrid.replaceChildren(...coverData.map((cover, index) => createCoverTile(cover, index, isRevealed)));
    coverGrid.setAttribute(
      "aria-label",
      isRevealed ? "A mosaic of ten imported fictional library covers" : "A mosaic of ten library cover placeholders"
    );
  }

  function buildTodayArtwork() {
    const strip = document.createElement("span");
    strip.className = "preview-strip";
    coverData.slice(0, 5).forEach((cover) => strip.append(createArtworkElement(cover, "preview-strip-tile")));
    todayArtwork.replaceChildren(strip);
  }

  function setTilesRevealed(tiles) {
    tiles.forEach((tile) => {
      tile.classList.add("is-revealed");
      const art = tile.querySelector(".cover-art");
      if (art) art.style.opacity = "1";
    });
  }

  function updateSyncContent() {
    const provider = currentProvider();
    const syncChip = document.querySelector("#sync-provider-chip");
    const syncStage = document.querySelector("#sync-stage");
    const syncTitle = document.querySelector("#sync-title");
    const syncSupporting = document.querySelector("#sync-supporting");
    const importStatus = document.querySelector("#import-status");
    const importCount = document.querySelector("#import-count");
    const importNote = document.querySelector("#import-note");

    syncChip.textContent = provider.chip;
    importNote.textContent = `Your ${provider.possessive} library stays the source of truth.`;
    importCard.hidden = false;
    todayPreview.hidden = state.state !== "complete";
    importCard.classList.toggle("is-complete", state.state === "complete");

    if (state.state === "complete") {
      syncStage.textContent = "READY FOR TODAY";
      syncTitle.textContent = "Library imported";
      syncSupporting.textContent = `Your ${provider.possessive} library stays the source of truth.`;
      importStatus.textContent = "38 titles imported";
      importCount.textContent = "38 titles imported";
      buildTodayArtwork();
      return;
    }

    if (state.phase === "returning") {
      syncStage.textContent = "SIMULATED RETURN";
      syncSupporting.textContent = `Mio is returning from ${provider.label} with your collection ready to import.`;
      importStatus.textContent = "Preparing 38 titles";
    } else {
      syncStage.textContent = "IMPORTING";
      syncSupporting.textContent = `Reading the collection that lives in ${provider.label}.`;
      importStatus.textContent = "Importing 38 titles";
    }

    syncTitle.textContent = "Bringing your library into focus";
    importCount.textContent = "38 titles found";
  }

  function animateScreenIn(screen, direction) {
    if (!screen) return;
    const travel = direction === "back" ? -18 : 18;
    const keyframes = shouldReduceMotion()
      ? [{ opacity: 0 }, { opacity: 1 }]
      : [{ opacity: 0, transform: `translateX(${travel}px)` }, { opacity: 1, transform: "translateX(0)" }];
    animateElement(screen, keyframes, { duration: shouldReduceMotion() ? 100 : 220, slow: false });
  }

  function switchScreen(screenName) {
    const target = document.querySelector(`[data-screen="${screenName}"]`);
    if (!target) return;

    const currentScreen = [welcomeScreen, syncScreen].find((screen) => !screen.hidden);
    if (currentScreen === target) {
      target.hidden = false;
      return;
    }

    if (currentScreen) currentScreen.hidden = true;
    target.hidden = false;
    appMain.scrollTop = 0;
    animateScreenIn(target, screenName === "welcome" ? "back" : "forward");
  }

  function animateSyncChange() {
    const reduced = shouldReduceMotion();
    animateElement(
      syncHeadingBlock,
      reduced ? [{ opacity: 0.5 }, { opacity: 1 }] : [{ opacity: 0.38, transform: "translateY(8px)" }, { opacity: 1, transform: "translateY(0)" }],
      { duration: reduced ? 100 : 190, slow: false }
    );
    animateElement(
      importCard,
      reduced ? [{ opacity: 0.6 }, { opacity: 1 }] : [{ opacity: 0.58, transform: "translateY(8px)" }, { opacity: 1, transform: "translateY(0)" }],
      { duration: reduced ? 100 : 190, delay: reduced ? 0 : 35, slow: false }
    );
    if (state.state === "complete") {
      animateElement(
        todayPreview,
        reduced ? [{ opacity: 0 }, { opacity: 1 }] : [{ opacity: 0, transform: "translateY(10px)" }, { opacity: 1, transform: "translateY(0)" }],
        { duration: reduced ? 100 : 210, delay: reduced ? 0 : 100, slow: false }
      );
    }
  }

  function renderApp(previousState, options = {}) {
    updateControls();
    updateSyncContent();
    switchScreen(state.state === "welcome" ? "welcome" : "sync");

    if (options.animateSync && previousState !== state.state && state.state !== "importing") {
      animateSyncChange();
    }
  }

  function prepareTodayPreview(mode) {
    todayPreview.hidden = false;
    todayPreview.classList.add("is-entering");
    clearMotionInline(todayPreview);
    clearMotionInline(todayPreviewTopline);
    clearMotionInline(todayArtwork);
    clearMotionInline(previewCopy);
    clearMotionInline(previewCaption);

    if (mode === "whole") {
      todayPreview.style.opacity = "0";
      return;
    }

    todayPreviewTopline.style.opacity = "0";
    todayArtwork.style.opacity = "0";
    previewCopy.style.opacity = "0";
    previewCaption.style.opacity = "0";
  }

  function animatePreviewText(delay = 0) {
    const reduced = shouldReduceMotion();
    const keyframes = reduced ? [{ opacity: 0 }, { opacity: 1 }] : [{ opacity: 0, transform: "translateY(8px)" }, { opacity: 1, transform: "translateY(0)" }];
    [todayPreviewTopline, previewCopy, previewCaption].forEach((element, index) => {
      animateElement(element, keyframes, {
        duration: reduced ? 120 : 190,
        delay: delay + index * 24,
        slow: true
      });
    });
  }

  function showCompletionToast(runId, delay) {
    scheduleMotion(() => {
      if (runId !== importRun || state.state !== "complete") return;
      showToast(`38 titles ready in Today · ${currentProvider().label}`);
    }, delay);
  }

  function completeImport(runId, completion) {
    if (runId !== importRun || state.state !== "importing") return;

    state.state = "complete";
    state.phase = "complete";
    updateControls();
    updateSyncContent();
    completion();
  }

  function completeCoverDeal(runId) {
    prepareTodayPreview("staged");
    coverGridStage.classList.add("is-compressing");
    animateElement(
      coverGridStage,
      [{ transform: "scaleY(1)" }, { transform: "translateY(3px) scaleY(0.58)" }],
      { duration: 260, easing: "cubic-bezier(0.22, 0.9, 0.28, 1)", slow: true }
    );
    animateElement(
      todayArtwork,
      [{ opacity: 0, transform: "translateY(6px) scaleY(0.64)" }, { opacity: 1, transform: "translateY(0) scaleY(1)" }],
      { duration: 230, delay: 55, easing: "cubic-bezier(0.22, 1, 0.36, 1)", slow: true }
    );
    scheduleMotion(() => {
      if (runId !== importRun || state.state !== "complete") return;
      animatePreviewText(0);
    }, 300);
    scheduleMotion(() => {
      if (runId !== importRun || state.state !== "complete") return;
      importCard.hidden = true;
    }, 610);
    showCompletionToast(runId, 500);
  }

  function completePanelWipe(runId) {
    prepareTodayPreview("whole");
    importCard.classList.add("is-crossfading");
    animateElement(importCard, [{ opacity: 1 }, { opacity: 0 }], { duration: 260, easing: "cubic-bezier(0.22, 1, 0.36, 1)", slow: true });
    animateElement(todayPreview, [{ opacity: 0 }, { opacity: 1 }], { duration: 260, easing: "cubic-bezier(0.22, 1, 0.36, 1)", slow: true });
    scheduleMotion(() => {
      if (runId !== importRun || state.state !== "complete") return;
      importCard.hidden = true;
    }, 330);
    showCompletionToast(runId, 390);
  }

  function completeMosaicAssembly(runId) {
    prepareTodayPreview("staged");
    coverGridStage.classList.add("is-compressing");
    animateElement(
      coverGridStage,
      [{ transform: "scale(1)" }, { transform: "translateY(-3px) scale(0.82, 0.56)" }],
      { duration: 320, easing: "cubic-bezier(0.22, 1, 0.36, 1)", slow: true }
    );
    animateElement(
      importCardBottom,
      [{ opacity: 1, transform: "translateY(0)" }, { opacity: 0, transform: "translateY(-14px)" }],
      { duration: 250, delay: 25, easing: "cubic-bezier(0.22, 1, 0.36, 1)", slow: true }
    );
    animateElement(
      todayArtwork,
      [{ opacity: 0, transform: "translateY(-10px) scale(0.92)" }, { opacity: 1, transform: "translateY(0) scale(1)" }],
      { duration: 250, delay: 100, easing: "cubic-bezier(0.22, 1, 0.36, 1)", slow: true }
    );
    scheduleMotion(() => {
      if (runId !== importRun || state.state !== "complete") return;
      animatePreviewText(0);
    }, 350);
    scheduleMotion(() => {
      if (runId !== importRun || state.state !== "complete") return;
      importCard.hidden = true;
    }, 650);
    showCompletionToast(runId, 540);
  }

  function completeReducedImport(runId) {
    prepareTodayPreview("whole");
    animateElement(importCard, [{ opacity: 1 }, { opacity: 0 }], { duration: 180, reducedDuration: 180, slow: false });
    animateElement(todayPreview, [{ opacity: 0 }, { opacity: 1 }], { duration: 180, reducedDuration: 180, slow: false });
    schedule(() => {
      if (runId !== importRun || state.state !== "complete") return;
      importCard.hidden = true;
    }, 230);
    showCompletionToast(runId, 260);
  }

  function runCoverDeal(runId) {
    coverGrid.classList.add("is-dealing");
    const tiles = [...coverGrid.querySelectorAll(".cover-tile")];
    tiles.forEach((tile, index) => {
      tile.classList.add("is-revealed");
      tile.style.zIndex = String(10 + index);
      const rotation = index % 2 === 0 ? -3 : 3;
      const art = tile.querySelector(".cover-art");
      if (art) art.style.opacity = "1";
      animateElement(
        tile,
        [
          { opacity: 0, transform: `translateY(96px) rotate(${rotation}deg)` },
          { opacity: 1, transform: `translateY(-3px) rotate(${rotation * 0.12}deg)` },
          { opacity: 1, transform: "translateY(0) rotate(0deg)" }
        ],
        { duration: 320, delay: index * 72, easing: "cubic-bezier(0.18, 0.9, 0.26, 1)", slow: true }
      );
    });

    scheduleMotion(() => {
      completeImport(runId, () => completeCoverDeal(runId));
    }, 1480);
  }

  function panelGroupBounds(group) {
    const stageRect = coverGridStage.getBoundingClientRect();
    const rects = group.map((tile) => tile.getBoundingClientRect());
    return {
      left: Math.min(...rects.map((rect) => rect.left)) - stageRect.left,
      right: Math.max(...rects.map((rect) => rect.right)) - stageRect.left,
      top: Math.min(...rects.map((rect) => rect.top)) - stageRect.top,
      stageRect
    };
  }

  function addPanelTrackingLine(group, direction) {
    const bounds = panelGroupBounds(group);
    const groupWidth = bounds.right - bounds.left;
    const lineWidth = Math.min(56, Math.max(30, groupWidth * 0.32));
    const line = document.createElement("span");
    line.className = "panel-tracking-line";
    line.style.left = `${bounds.left}px`;
    line.style.top = `${bounds.top + 8}px`;
    line.style.width = `${lineWidth}px`;
    coverGridStage.append(line);

    const startTransform = direction === "ltr" ? `translateX(-${lineWidth}px)` : `translateX(${groupWidth}px)`;
    const endTransform = direction === "ltr" ? `translateX(${groupWidth}px)` : `translateX(-${lineWidth}px)`;
    animateElement(
      line,
      [
        { opacity: 0, transform: startTransform },
        { opacity: 1, transform: startTransform },
        { opacity: 1, transform: endTransform },
        { opacity: 0, transform: endTransform }
      ],
      { duration: 280, easing: "cubic-bezier(0.77, 0, 0.175, 1)", slow: true }
    );
    scheduleMotion(() => line.remove(), 330);
  }

  function revealPanelGroup(group, groupIndex, runId) {
    if (runId !== importRun || state.state !== "importing") return;
    const direction = groupIndex % 2 === 0 ? "ltr" : "rtl";
    const hiddenClip = direction === "ltr" ? "inset(0 100% 0 0)" : "inset(0 0 0 100%)";
    addPanelTrackingLine(group, direction);

    group.forEach((tile) => {
      tile.classList.add("is-wiping");
      const art = tile.querySelector(".cover-art");
      animateElement(
        art,
        [{ opacity: 0.82, clipPath: hiddenClip }, { opacity: 1, clipPath: "inset(0 0 0 0)" }],
        { duration: 270, easing: "cubic-bezier(0.77, 0, 0.175, 1)", slow: true }
      );
      scheduleMotion(() => {
        if (runId !== importRun || state.state !== "importing") return;
        tile.classList.remove("is-wiping");
        tile.classList.add("is-revealed");
        clearMotionInline(art);
      }, 300);
    });
  }

  function runPanelWipe(runId) {
    coverGrid.classList.add("is-panel-wiping");
    const tiles = [...coverGrid.querySelectorAll(".cover-tile")];
    const groups = [tiles.slice(0, 3), tiles.slice(3, 5), tiles.slice(5, 8), tiles.slice(8, 10)];
    groups.forEach((group, groupIndex) => {
      scheduleMotion(() => revealPanelGroup(group, groupIndex, runId), groupIndex * 340);
    });
    scheduleMotion(() => {
      completeImport(runId, () => completePanelWipe(runId));
    }, 1480);
  }

  function createAssemblyStack() {
    const stack = document.createElement("div");
    stack.className = "assembly-stack";
    stack.setAttribute("aria-hidden", "true");
    for (let index = 0; index < 4; index += 1) {
      const card = document.createElement("span");
      card.className = "assembly-stack-card";
      stack.append(card);
    }
    coverGridStage.append(stack);
    return stack;
  }

  function beginMosaicAssembly(runId, stack) {
    if (runId !== importRun || state.state !== "importing") return;
    const stageRect = coverGridStage.getBoundingClientRect();
    const stackRect = stack.getBoundingClientRect();
    const stackLeft = stackRect.left - stageRect.left;
    const stackTop = stackRect.top - stageRect.top;
    const stackCenterX = stackLeft + stackRect.width / 2;
    const stackCenterY = stackTop + stackRect.height / 2;
    const tiles = [...coverGrid.querySelectorAll(".cover-tile")];

    tiles.forEach((tile, index) => {
      const cover = coverData[index];
      const tileRect = tile.getBoundingClientRect();
      const targetLeft = tileRect.left - stageRect.left;
      const targetTop = tileRect.top - stageRect.top;
      const startLeft = stackCenterX - tileRect.width / 2;
      const startTop = stackCenterY - tileRect.height / 2;
      const deltaX = targetLeft - startLeft;
      const deltaY = targetTop - startTop;
      const rowDelay = index < 5 ? 0 : 220;
      const microDelay = (index % 5) * 24;
      const flyer = document.createElement("span");
      flyer.className = "assembly-flyer";
      flyer.dataset.index = String(index);
      flyer.style.left = `${startLeft}px`;
      flyer.style.top = `${startTop}px`;
      flyer.style.width = `${tileRect.width}px`;
      flyer.style.height = `${tileRect.height}px`;
      flyer.append(createArtworkElement(cover, "cover-art"));
      coverGridStage.append(flyer);

      animateElement(
        flyer,
        [
          { opacity: 0.96, transform: `translate(0px, 0px) rotate(${index % 2 === 0 ? -4 : 4}deg)` },
          { opacity: 1, transform: `translate(${deltaX}px, ${deltaY}px) rotate(0deg)` }
        ],
        { duration: 480, delay: rowDelay + microDelay, easing: "cubic-bezier(0.23, 1, 0.32, 1)", slow: true }
      );

      scheduleMotion(() => {
        if (runId !== importRun || state.state !== "importing") return;
        tile.classList.add("is-revealed");
        const art = tile.querySelector(".cover-art");
        if (art) {
          art.style.opacity = "0";
          animateElement(art, [{ opacity: 0 }, { opacity: 1 }], { duration: 150, easing: "cubic-bezier(0.22, 1, 0.36, 1)", slow: true });
        }
        animateElement(flyer, [{ opacity: 1 }, { opacity: 0 }], { duration: 150, easing: "cubic-bezier(0.22, 1, 0.36, 1)", slow: true });
        scheduleMotion(() => flyer.remove(), 175);
      }, rowDelay + microDelay + 500);
    });

    animateElement(stack, [{ opacity: 1 }, { opacity: 0 }], { duration: 220, delay: 250, easing: "cubic-bezier(0.22, 1, 0.36, 1)", slow: true });
    scheduleMotion(() => stack.remove(), 520);
  }

  function runMosaicAssembly(runId) {
    coverGridStage.classList.add("is-assembling");
    const stack = createAssemblyStack();
    scheduleFrame(() => beginMosaicAssembly(runId, stack));
    scheduleMotion(() => {
      completeImport(runId, () => completeMosaicAssembly(runId));
    }, 1440);
  }

  function runReducedImport(runId) {
    coverGrid.classList.add("is-reduced-reveal");
    const tiles = [...coverGrid.querySelectorAll(".cover-tile")];
    setTilesRevealed(tiles);
    tiles.forEach((tile) => {
      const art = tile.querySelector(".cover-art");
      if (!art) return;
      art.style.opacity = "0";
      animateElement(art, [{ opacity: 0 }, { opacity: 1 }], { duration: 180, reducedDuration: 180, slow: false });
    });
    schedule(() => {
      completeImport(runId, () => completeReducedImport(runId));
    }, 260);
  }

  function startImportSequence(runId) {
    if (runId !== importRun || state.state !== "importing") return;

    state.phase = "importing";
    updateControls();
    updateSyncContent();
    renderCovers(false);

    if (shouldReduceMotion()) {
      runReducedImport(runId);
      return;
    }

    const motionRunner = motionRunners[state.variant] || runCoverDeal;
    motionRunner(runId);
  }

  function showToast(message, duration = 2400) {
    appToast.textContent = message;
    appToast.hidden = false;
    const reduced = shouldReduceMotion();
    animateElement(
      appToast,
      reduced ? [{ opacity: 0 }, { opacity: 1 }] : [{ opacity: 0, transform: "translateY(8px)" }, { opacity: 1, transform: "translateY(0)" }],
      { duration: reduced ? 90 : 180, slow: false }
    );
    schedule(() => {
      appToast.hidden = true;
      appToast.textContent = "";
    }, duration);
  }

  function showState(nextState, options = {}) {
    if (!isValidState(nextState)) return;

    const previousState = state.state;
    cancelRunningAnimations();
    importRun += 1;
    const runId = importRun;

    state.state = nextState;
    state.phase = nextState === "welcome" ? "idle" : nextState === "complete" ? "complete" : options.source === "provider" ? "returning" : "importing";

    if (nextState === "complete") {
      renderCovers(true);
      renderApp(previousState, { animateSync: true });
      showToast(`38 titles ready in Today · ${currentProvider().label}`);
      return;
    }

    if (nextState === "welcome") {
      renderCovers(false);
      renderApp(previousState);
      return;
    }

    renderCovers(false);
    renderApp(previousState);

    if (state.phase === "returning") {
      scheduleMotion(() => startImportSequence(runId), 700);
    } else {
      startImportSequence(runId);
    }
  }

  function setVariant(variant) {
    if (!isValidVariant(variant) || variant === state.variant) return;

    cancelRunningAnimations();
    state.variant = variant;
    importRun += 1;
    updateControls();

    if (state.state === "importing") {
      state.phase = "importing";
      renderCovers(false);
      renderApp("importing");
      startImportSequence(importRun);
      return;
    }

    if (state.state === "complete") renderCovers(true);
    else renderCovers(false);
    renderApp(state.state);
  }

  function setProvider(provider) {
    if (!isValidProvider(provider) || provider === state.provider) return;

    cancelRunningAnimations();
    state.provider = provider;
    importRun += 1;
    updateControls();

    if (state.state === "importing") {
      state.phase = "importing";
      renderCovers(false);
      renderApp("importing");
      startImportSequence(importRun);
      return;
    }

    renderCovers(state.state === "complete");
    renderApp(state.state);
  }

  function setReducedMotion() {
    cancelRunningAnimations();
    state.reducedMotion = !state.reducedMotion;
    importRun += 1;
    updateControls();

    if (state.state === "importing") {
      state.phase = "importing";
      renderCovers(false);
      renderApp("importing");
      startImportSequence(importRun);
      return;
    }

    renderCovers(state.state === "complete");
    renderApp(state.state);
  }

  function setSlowMotion() {
    cancelRunningAnimations();
    state.slowMotion = !state.slowMotion;
    importRun += 1;
    updateControls();

    if (state.state === "importing") {
      state.phase = "importing";
      renderCovers(false);
      renderApp("importing");
      startImportSequence(importRun);
      return;
    }

    renderCovers(state.state === "complete");
    renderApp(state.state);
  }

  function replay() {
    cancelRunningAnimations();
    importRun += 1;
    state.state = "welcome";
    state.phase = "idle";
    renderCovers(false);
    renderApp("replay");
  }

  function startProviderFlow(provider) {
    if (!isValidProvider(provider) || state.state !== "welcome") return;
    state.provider = provider;
    showState("importing", { source: "provider" });
  }

  function bindExplicitKeyboardActivation(button, handler) {
    if (!button) return;

    button.addEventListener("click", (event) => {
      if (button._suppressClick) {
        button._suppressClick = false;
        if (button._suppressClickTimer) window.clearTimeout(button._suppressClickTimer);
        managedTimers.delete(button._suppressClickTimer);
        button._suppressClickTimer = null;
        return;
      }
      handler(event);
    });

    button.addEventListener("keydown", (event) => {
      if (!["Enter", " "].includes(event.key) || event.repeat) return;
      event.preventDefault();
      if (button._keyboardActive) return;
      button._keyboardActive = true;
      button._suppressClick = true;
      if (button._suppressClickTimer) window.clearTimeout(button._suppressClickTimer);
      button._suppressClickTimer = schedule(() => {
        button._suppressClick = false;
        button._suppressClickTimer = null;
      }, 250);
      handler(event);
    });

    button.addEventListener("keyup", (event) => {
      if (["Enter", " "].includes(event.key)) button._keyboardActive = false;
    });

    button.addEventListener("blur", () => {
      button._keyboardActive = false;
    });
  }

  const motionRunners = {
    a: runCoverDeal,
    b: runPanelWipe,
    c: runMosaicAssembly
  };

  controlButtons.forEach((button) => {
    bindExplicitKeyboardActivation(button, () => {
      const control = button.dataset.control;
      const value = button.dataset.value;
      if (control === "variant") setVariant(value);
      if (control === "state") showState(value, { source: "control" });
      if (control === "provider") setProvider(value);
    });
  });

  actionButtons.forEach((button) => {
    bindExplicitKeyboardActivation(button, () => {
      if (button.dataset.action === "replay") replay();
      if (button.dataset.action === "reduced-motion") setReducedMotion();
      if (button.dataset.action === "slow-motion") setSlowMotion();
    });
  });

  providerActionButtons.forEach((button) => {
    bindExplicitKeyboardActivation(button, () => startProviderFlow(button.dataset.providerAction));
  });

  bindExplicitKeyboardActivation(previewAction, () => {
    showToast(`Today is ready with ${currentProvider().label} as the source of truth.`);
  });

  reducedMotionQuery.addEventListener?.("change", () => {
    if (state.state !== "importing") return;
    cancelRunningAnimations();
    importRun += 1;
    state.phase = "importing";
    renderCovers(false);
    renderApp("importing");
    startImportSequence(importRun);
  });

  renderCovers(false);
  renderApp(null);
})();
