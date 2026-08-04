(() => {
  "use strict";

  const root = document.documentElement;
  const systemMotion = window.matchMedia("(prefers-reduced-motion: reduce)");

  const elements = {
    app: document.querySelector("#device-app"),
    scroller: document.querySelector("#phone-scroll"),
    heroArt: document.querySelector("#hero-art"),
    detailIntro: document.querySelector("#detail-intro"),
    mainPoster: document.querySelector("#main-poster"),
    introCopy: document.querySelector("#intro-copy"),
    toolbarSurface: document.querySelector("#toolbar-surface"),
    toolbarPoster: document.querySelector("#toolbar-poster"),
    toolbarTitle: document.querySelector("#toolbar-title"),
    toolbarLightIcons: [...document.querySelectorAll(".toolbar-icon-light")],
    toolbarDarkIcons: [...document.querySelectorAll(".toolbar-icon-dark")],
    scrollRange: document.querySelector("#scroll-range"),
    scrollOutput: document.querySelector("#scroll-output"),
    autoButton: document.querySelector("#auto-scroll-button"),
    autoLabel: document.querySelector("#auto-scroll-label"),
    resetButton: document.querySelector("#reset-button"),
    autoBadge: document.querySelector("#auto-badge"),
    statusVariant: document.querySelector("#status-variant"),
    statusScroll: document.querySelector("#status-scroll"),
    statusPhase: document.querySelector("#status-phase"),
    statusMotion: document.querySelector("#status-motion"),
    variantNote: document.querySelector("#variant-note"),
    liveStatus: document.querySelector("#live-status")
  };

  const variantButtons = [...document.querySelectorAll("[data-variant]")];
  const themeButtons = [...document.querySelectorAll("[data-theme-choice]")];
  const motionButtons = [...document.querySelectorAll("[data-motion-choice]")];
  const positionButtons = [...document.querySelectorAll("[data-position]")];

  const VARIANT_LABELS = {
    minimal: "Minimal Fade",
    parallax: "Gentle Parallax",
    handoff: "Poster Handoff",
    sheet: "Sheet Rise"
  };

  const VARIANT_NOTES = {
    minimal: "Baseline: native scrolling throughout; the toolbar surface fades from 0 to 1 between 130 and 190px.",
    parallax: "Artwork lags the scroll by up to 28px, with a smaller 8px lag on the poster and title copy. No scaling.",
    handoff: "The poster and title hand off to the toolbar across 116–260px: poster opacity 1→0, scale 1→0.82, toolbar copy 0→1.",
    sheet: "The artwork stays behind an opaque detail sheet; the sheet adds a direct 0→−48px rise across 128–248px."
  };

  const state = {
    variant: "minimal",
    theme: "light",
    motion: "full",
    autoScroll: false,
    scrollTop: 0,
    scrollPercent: 0,
    phase: "Expanded"
  };

  let renderFrame = 0;
  let autoFrame = 0;
  let autoLastTime = 0;

  const clamp = (value, min = 0, max = 1) => Math.min(max, Math.max(min, value));

  function between(value, start, end) {
    if (end === start) return value >= end ? 1 : 0;
    return clamp((value - start) / (end - start));
  }

  function smoothStep(value) {
    const t = clamp(value);
    return t * t * (3 - 2 * t);
  }

  function isReducedMotionActive() {
    return state.motion === "reduced" || systemMotion.matches;
  }

  function setPressed(buttons, activeButton) {
    buttons.forEach((button) => {
      const isActive = button === activeButton;
      button.classList.toggle("is-active", isActive);
      button.setAttribute("aria-pressed", String(isActive));
    });
  }

  function announce(message) {
    elements.liveStatus.textContent = "";
    window.setTimeout(() => {
      elements.liveStatus.textContent = message;
    }, 0);
  }

  function setTransform(element, x = 0, y = 0, scale = 1) {
    element.style.transform = `translate3d(${x.toFixed(2)}px, ${y.toFixed(2)}px, 0) scale(${scale.toFixed(4)})`;
  }

  function getMaxScroll() {
    return Math.max(0, elements.scroller.scrollHeight - elements.scroller.clientHeight);
  }

  function getPhase(scrollTop) {
    if (scrollTop < 130) return "Expanded";
    if (scrollTop <= 240) return "Transitioning";
    return "Collapsed";
  }

  function updatePositionButtons(scrollTop, maxScroll) {
    const transitionTarget = Math.min(160, maxScroll);
    const isTop = scrollTop < 20;
    const isTransition = Math.abs(scrollTop - transitionTarget) < 28;
    const isCollapsed = maxScroll > 0 && maxScroll - scrollTop < 20;

    positionButtons.forEach((button) => {
      const position = button.dataset.position;
      const isActive = (position === "top" && isTop) || (position === "transition" && !isTop && isTransition) || (position === "collapsed" && isCollapsed);
      button.classList.toggle("is-active", isActive);
      button.setAttribute("aria-pressed", String(isActive));
    });
  }

  function updateStatus(scrollTop, maxScroll) {
    const percent = maxScroll > 0 ? Math.round((scrollTop / maxScroll) * 100) : 0;
    const phase = getPhase(scrollTop);
    const reduced = isReducedMotionActive();

    state.scrollTop = scrollTop;
    state.scrollPercent = percent;
    state.phase = phase;

    elements.scrollRange.value = String(percent);
    elements.scrollOutput.value = `${percent}%`;
    elements.scrollOutput.textContent = `${percent}%`;
    elements.statusVariant.textContent = VARIANT_LABELS[state.variant];
    elements.statusScroll.textContent = `${percent}%`;
    elements.statusPhase.textContent = phase;
    elements.statusMotion.textContent = reduced ? "Active" : "Off";
    elements.variantNote.textContent = VARIANT_NOTES[state.variant];
    updatePositionButtons(scrollTop, maxScroll);
  }

  function updateAutoControls() {
    elements.autoButton.setAttribute("aria-pressed", String(state.autoScroll));
    elements.autoLabel.textContent = state.autoScroll ? "Stop auto-scroll" : "Auto-scroll";
    elements.autoButton.querySelector(".action-icon").textContent = state.autoScroll ? "■" : "▶";
    elements.autoBadge.hidden = !state.autoScroll;
  }

  function renderScroll() {
    renderFrame = 0;

    const maxScroll = getMaxScroll();
    const scrollTop = clamp(elements.scroller.scrollTop, 0, maxScroll);
    const reduced = isReducedMotionActive();
    const movement = reduced ? 0 : 1;
    const toolbarFade = between(scrollTop, 130, 190);
    const transition = smoothStep(between(scrollTop, 130, 240));
    const artworkProgress = smoothStep(between(scrollTop, 0, 264));

    elements.app.dataset.variant = state.variant;
    elements.app.dataset.reduced = String(reduced);

    let toolbarOpacity = toolbarFade;
    let heroY = 0;
    let posterY = 0;
    let posterScale = 1;
    let posterOpacity = 1;
    let introY = 0;
    let introOpacity = 1;
    let sheetY = 0;
    let toolbarPosterOpacity = 0;
    let toolbarPosterX = -10;
    let toolbarPosterScale = 0.84;
    let toolbarTitleOpacity = 0;
    let toolbarTitleY = 4;

    if (state.variant === "parallax") {
      heroY = artworkProgress * 28 * movement;
      posterY = artworkProgress * 8 * movement;
      introY = artworkProgress * 4 * movement;
    }

    if (state.variant === "handoff") {
      const handoff = smoothStep(between(scrollTop, 116, 260));
      toolbarOpacity = Math.max(toolbarOpacity, handoff);
      posterY = -52 * handoff * movement;
      posterScale = 1 - 0.18 * handoff * movement;
      posterOpacity = 1 - handoff;
      introY = -12 * handoff * movement;
      introOpacity = 1 - handoff;
      toolbarPosterOpacity = handoff;
      toolbarPosterX = -10 + 10 * handoff * movement;
      toolbarPosterScale = 0.84 + 0.16 * handoff * movement;
      toolbarTitleOpacity = handoff;
      toolbarTitleY = 4 - 4 * handoff * movement;
    }

    if (state.variant === "sheet") {
      const sheetProgress = smoothStep(between(scrollTop, 128, 248));
      toolbarOpacity = Math.max(toolbarFade, sheetProgress * 0.9);
      heroY = artworkProgress * 12 * movement;
      posterY = -20 * sheetProgress * movement;
      introY = -8 * sheetProgress * movement;
      sheetY = -48 * sheetProgress * movement;
    }

    setTransform(elements.heroArt, 0, heroY, 1);
    setTransform(elements.mainPoster, 0, posterY, posterScale);
    setTransform(elements.introCopy, 0, introY, 1);
    setTransform(elements.detailIntro, 0, sheetY, 1);
    setTransform(elements.toolbarPoster, toolbarPosterX, 0, toolbarPosterScale);
    setTransform(elements.toolbarTitle, 0, toolbarTitleY * movement, 1);

    elements.mainPoster.style.opacity = posterOpacity.toFixed(4);
    elements.introCopy.style.opacity = introOpacity.toFixed(4);
    elements.toolbarSurface.style.opacity = clamp(toolbarOpacity).toFixed(4);
    elements.toolbarPoster.style.opacity = toolbarPosterOpacity.toFixed(4);
    elements.toolbarTitle.style.opacity = toolbarTitleOpacity.toFixed(4);

    const lightIconOpacity = 1 - clamp(toolbarOpacity);
    const darkIconOpacity = clamp(toolbarOpacity);
    elements.toolbarLightIcons.forEach((icon) => {
      icon.style.opacity = lightIconOpacity.toFixed(4);
    });
    elements.toolbarDarkIcons.forEach((icon) => {
      icon.style.opacity = darkIconOpacity.toFixed(4);
    });

    updateStatus(scrollTop, maxScroll);
  }

  function scheduleRender() {
    if (renderFrame) return;
    renderFrame = window.requestAnimationFrame(renderScroll);
  }

  function stopAutoScroll() {
    state.autoScroll = false;
    autoLastTime = 0;
    if (autoFrame) {
      window.cancelAnimationFrame(autoFrame);
      autoFrame = 0;
    }
    updateAutoControls();
  }

  function autoScrollTick(timestamp) {
    if (!state.autoScroll) return;

    if (!autoLastTime) autoLastTime = timestamp;
    const elapsed = Math.min(32, timestamp - autoLastTime);
    autoLastTime = timestamp;
    const maxScroll = getMaxScroll();
    const nextTop = Math.min(maxScroll, elements.scroller.scrollTop + elapsed * 0.065);

    elements.scroller.scrollTop = nextTop;
    if (nextTop >= maxScroll - 1) {
      stopAutoScroll();
      scheduleRender();
      return;
    }

    autoFrame = window.requestAnimationFrame(autoScrollTick);
  }

  function startAutoScroll() {
    const maxScroll = getMaxScroll();
    if (maxScroll <= 0) return;
    if (elements.scroller.scrollTop >= maxScroll - 1) elements.scroller.scrollTop = 0;

    state.autoScroll = true;
    updateAutoControls();
    autoLastTime = 0;
    autoFrame = window.requestAnimationFrame(autoScrollTick);
  }

  function toggleAutoScroll() {
    if (state.autoScroll) {
      stopAutoScroll();
    } else {
      startAutoScroll();
    }
  }

  function scrollToPosition(position) {
    stopAutoScroll();
    const maxScroll = getMaxScroll();
    const target = position === "top" ? 0 : position === "transition" ? Math.min(160, maxScroll) : maxScroll;
    elements.scroller.scrollTop = target;
    scheduleRender();
  }

  function changeVariant(button) {
    state.variant = button.dataset.variant;
    setPressed(variantButtons, button);
    elements.app.dataset.variant = state.variant;
    scheduleRender();
    announce(`${VARIANT_LABELS[state.variant]} selected`);
  }

  function changeTheme(button) {
    state.theme = button.dataset.themeChoice;
    root.dataset.theme = state.theme;
    setPressed(themeButtons, button);
    scheduleRender();
    announce(`${state.theme === "dark" ? "Dark" : "Light"} theme selected`);
  }

  function changeMotion(button) {
    state.motion = button.dataset.motionChoice;
    root.dataset.motion = state.motion;
    setPressed(motionButtons, button);
    scheduleRender();
    announce(`${state.motion === "reduced" ? "Reduced" : "Full"} motion selected`);
  }

  variantButtons.forEach((button) => button.addEventListener("click", () => changeVariant(button)));
  themeButtons.forEach((button) => button.addEventListener("click", () => changeTheme(button)));
  motionButtons.forEach((button) => button.addEventListener("click", () => changeMotion(button)));
  positionButtons.forEach((button) => button.addEventListener("click", () => scrollToPosition(button.dataset.position)));

  elements.scrollRange.addEventListener("input", (event) => {
    stopAutoScroll();
    const maxScroll = getMaxScroll();
    const percent = Number(event.target.value) / 100;
    elements.scroller.scrollTop = maxScroll * percent;
    scheduleRender();
  });

  elements.autoButton.addEventListener("click", toggleAutoScroll);
  elements.resetButton.addEventListener("click", () => scrollToPosition("top"));

  elements.scroller.addEventListener("scroll", scheduleRender, { passive: true });
  elements.scroller.addEventListener("pointerdown", stopAutoScroll, { passive: true });
  elements.scroller.addEventListener("wheel", stopAutoScroll, { passive: true });
  elements.scroller.addEventListener("touchstart", stopAutoScroll, { passive: true });
  elements.scroller.addEventListener("keydown", (event) => {
    if (["ArrowDown", "ArrowUp", "PageDown", "PageUp", "Home", "End", " "].includes(event.key)) stopAutoScroll();
  });

  window.addEventListener("resize", scheduleRender, { passive: true });

  const handleSystemMotionChange = () => {
    scheduleRender();
    announce(isReducedMotionActive() ? "Reduced motion is active" : "Full motion is active");
  };

  if (typeof systemMotion.addEventListener === "function") {
    systemMotion.addEventListener("change", handleSystemMotionChange);
  } else if (typeof systemMotion.addListener === "function") {
    systemMotion.addListener(handleSystemMotionChange);
  }

  elements.app.dataset.variant = state.variant;
  elements.app.dataset.reduced = String(isReducedMotionActive());
  updateAutoControls();
  renderScroll();
})();
