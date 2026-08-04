(() => {
  "use strict";

  const root = document.documentElement;
  const systemMotion = window.matchMedia("(prefers-reduced-motion: reduce)");

  const elements = {
    card: document.querySelector("#now-card"),
    fill: document.querySelector("#progress-fill"),
    track: document.querySelector("#progress-track"),
    dot: document.querySelector("#progress-dot"),
    trace: document.querySelector("#edge-trace-path"),
    fxLayer: document.querySelector("#fx-layer"),
    currentLabel: document.querySelector("#progress-label-current"),
    completeLabel: document.querySelector("#progress-label-complete"),
    badge: document.querySelector("#completion-badge"),
    plus: document.querySelector("#plus-button"),
    minus: document.querySelector("#minus-button"),
    undo: document.querySelector("#undo-button"),
    snackbar: document.querySelector("#snackbar"),
    replay: document.querySelector("#replay-button"),
    demoStatus: document.querySelector("#demo-status"),
    motionNote: document.querySelector("#motion-note"),
    liveStatus: document.querySelector("#live-status")
  };

  const variantButtons = [...document.querySelectorAll("[data-variant]")];
  const themeButtons = [...document.querySelectorAll("[data-theme-choice]")];
  const motionButtons = [...document.querySelectorAll("[data-motion-choice]")];
  const navButtons = [...document.querySelectorAll(".nav-destination")];

  const TOTAL_EPISODES = 12;
  const START_EPISODE = 11;
  const START_RATIO = START_EPISODE / TOTAL_EPISODES;
  const easing = "cubic-bezier(0.23, 1, 0.32, 1)";
  const settleEasing = "cubic-bezier(0.22, 1, 0.36, 1)";

  // The state object is the single source of truth for the comparison.
  const state = {
    progress: START_EPISODE,
    completed: false,
    variant: "finish",
    theme: "light",
    motion: "full",
    runToken: 0,
    hapticFired: false
  };

  const celebrationAnimations = new Set();
  const scheduledTimeouts = new Set();
  const scheduledFrames = new Set();
  let activePointerId = null;
  let keyboardPressActive = false;
  let keyboardClickObserved = false;
  let keyboardCommitHandled = false;

  function isCurrentRun(token) {
    return token === state.runToken && state.completed;
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

  function shouldReduceMotion() {
    return state.motion === "reduced" || systemMotion.matches;
  }

  function updateMotionNote() {
    if (state.motion === "reduced") {
      elements.motionNote.textContent = "Reduced motion · explicit setting";
    } else if (systemMotion.matches) {
      elements.motionNote.textContent = "Reduced motion · system preference active";
    } else {
      elements.motionNote.textContent = "Full motion · system preference respected";
    }
  }

  function updateDemoStatus() {
    elements.demoStatus.textContent = state.completed
      ? "Completed: Episode 12 of 12"
      : `Ready: Episode ${state.progress} of ${TOTAL_EPISODES}`;
  }

  function updateProgressAccessibility() {
    const label = state.completed
      ? "Completed · 12 episodes"
      : `Episode ${state.progress} of ${TOTAL_EPISODES}`;

    elements.track.setAttribute("aria-valuenow", String(state.progress));
    elements.track.setAttribute("aria-valuetext", label);
    elements.currentLabel.textContent = `Episode ${state.progress} of ${TOTAL_EPISODES}`;
    elements.currentLabel.setAttribute("aria-hidden", String(state.completed));
    elements.completeLabel.setAttribute("aria-hidden", String(!state.completed));
  }

  function renderProgress(progress) {
    const ratio = progress / TOTAL_EPISODES;
    elements.fill.style.transform = `scaleX(${ratio})`;
    elements.currentLabel.textContent = `Episode ${progress} of ${TOTAL_EPISODES}`;
    elements.currentLabel.style.opacity = "1";
    elements.completeLabel.style.opacity = "0";
    elements.currentLabel.setAttribute("aria-hidden", "false");
    elements.completeLabel.setAttribute("aria-hidden", "true");
    elements.track.setAttribute("aria-valuenow", String(progress));
    elements.track.setAttribute("aria-valuetext", `Episode ${progress} of ${TOTAL_EPISODES}`);
  }

  function clearTransientEffects() {
    elements.fxLayer.replaceChildren();
    elements.trace.style.opacity = "0";
    elements.trace.style.strokeDashoffset = "0";
    elements.dot.style.opacity = "0";
    elements.dot.style.transform = "translateY(-50%) scale(0.7)";
    elements.badge.style.opacity = "0";
    elements.badge.style.transform = "scale(0.96)";
    elements.badge.setAttribute("aria-hidden", "true");
    elements.card.style.transform = "";
  }

  function cancelRunningAnimations() {
    state.runToken += 1;

    celebrationAnimations.forEach((animation) => {
      try {
        animation.cancel();
      } catch (error) {
        // A finished animation can already be detached by the browser.
      }
    });
    celebrationAnimations.clear();

    scheduledTimeouts.forEach((timeoutId) => window.clearTimeout(timeoutId));
    scheduledTimeouts.clear();

    scheduledFrames.forEach((frameId) => window.cancelAnimationFrame(frameId));
    scheduledFrames.clear();

    clearTransientEffects();
  }

  function trackAnimation(element, keyframes, options) {
    const animation = element.animate(keyframes, options);
    celebrationAnimations.add(animation);
    return animation;
  }

  function animateElement(element, keyframes, options) {
    const animation = trackAnimation(element, keyframes, options);

    return new Promise((resolve) => {
      let settled = false;

      const finish = (didFinish) => {
        if (settled) return;
        settled = true;
        resolve(didFinish);
      };

      animation.addEventListener("finish", () => finish(true), { once: true });
      animation.addEventListener("cancel", () => finish(false), { once: true });
    });
  }

  function schedule(delay, token, callback) {
    const timeoutId = window.setTimeout(() => {
      scheduledTimeouts.delete(timeoutId);
      if (isCurrentRun(token)) callback();
    }, delay);

    scheduledTimeouts.add(timeoutId);
  }

  function firePeakHaptic(token) {
    const frameId = window.requestAnimationFrame(() => {
      scheduledFrames.delete(frameId);
      if (!isCurrentRun(token) || state.hapticFired) return;

      state.hapticFired = true;
      if (typeof navigator.vibrate === "function") {
        try {
          navigator.vibrate(18);
        } catch (error) {
          // Vibration is optional feedback and can be rejected by the browser.
        }
      }
    });

    scheduledFrames.add(frameId);
  }

  function schedulePeakHaptic(delay, token) {
    schedule(delay, token, () => firePeakHaptic(token));
  }

  function animateLabelCrossfade(token, reduced = false, delay = 0) {
    schedule(delay, token, () => {
      const duration = 160;
      animateElement(
        elements.currentLabel,
        [{ opacity: 1 }, { opacity: 0 }],
        { duration, easing: "ease-out", fill: "forwards" }
      );
      animateElement(
        elements.completeLabel,
        [{ opacity: 0 }, { opacity: 1 }],
        { duration, easing: "ease-out", fill: "forwards" }
      );

      if (reduced) {
        elements.currentLabel.style.transform = "none";
        elements.completeLabel.style.transform = "none";
      }
    });
  }

  function animateProgressUpdate(token) {
    elements.fill.style.transform = `scaleX(${START_RATIO})`;
    animateElement(
      elements.fill,
      [{ transform: `scaleX(${START_RATIO})` }, { transform: "scaleX(1)" }],
      { duration: 220, easing, fill: "forwards" }
    );

    schedule(150, token, () => {
      animateElement(
        elements.dot,
        [
          { opacity: 0, transform: "translateY(-50%) scale(0.7)" },
          { offset: 0.45, opacity: 1, transform: "translateY(-50%) scale(1.15)" },
          { opacity: 1, transform: "translateY(-50%) scale(1)" }
        ],
        { duration: 180, easing: settleEasing, fill: "forwards" }
      );
    });
  }

  function animateCard(token, keyframes, duration) {
    if (!isCurrentRun(token)) return;

    animateElement(elements.card, keyframes, {
      duration,
      easing: settleEasing,
      fill: "forwards"
    });
  }

  function showBadge(token, reduced = false, delay = 0) {
    schedule(delay, token, () => {
      elements.badge.setAttribute("aria-hidden", "false");

      const keyframes = reduced
        ? [{ opacity: 0, transform: "none" }, { opacity: 1, transform: "none" }]
        : [{ opacity: 0, transform: "scale(0.96)" }, { opacity: 1, transform: "scale(1)" }];

      animateElement(elements.badge, keyframes, {
        duration: reduced ? 160 : 220,
        easing: "ease-out",
        fill: "forwards"
      });
    });
  }

  function emitParticles(token, count, origin, positions, duration) {
    if (!isCurrentRun(token)) return;

    elements.fxLayer.replaceChildren();

    positions.slice(0, count).forEach((position, index) => {
      const particle = document.createElement("span");
      particle.className = `particle${index % 2 ? " is-diamond" : ""}`;
      particle.style.left = `${origin.x}px`;
      particle.style.top = `${origin.y}px`;
      elements.fxLayer.appendChild(particle);

      const animation = trackAnimation(
        particle,
        [
          { opacity: 0, transform: "translate(0, 0) scale(0.75)" },
          { offset: 0.16, opacity: 0.95, transform: "translate(0, 0) scale(1)" },
          {
            opacity: 0,
            transform: `translate(${position.x}px, ${position.y}px) scale(0.72)`
          }
        ],
        { duration, easing: settleEasing, fill: "forwards" }
      );

      animation.addEventListener("finish", () => particle.remove(), { once: true });
    });
  }

  function startFinishLineUpdate(token, cardKeyframes, cardDuration) {
    animateProgressUpdate(token);
    animateCard(token, cardKeyframes, cardDuration);
    animateLabelCrossfade(token, false, 150);
  }

  function playFinishLine(token = state.runToken) {
    startFinishLineUpdate(
      token,
      [
        { transform: "translateY(0) scale(1)" },
        { offset: 0.38, transform: "translateY(-4px) scale(1)" },
        { transform: "translateY(0) scale(1)" }
      ],
      440
    );
    showBadge(token, false, 300);
    schedulePeakHaptic(235, token);
  }

  function playEdgeSpark(token = state.runToken) {
    startFinishLineUpdate(
      token,
      [
        { transform: "translateY(0) scale(1)" },
        { offset: 0.42, transform: "translateY(-3px) scale(1.012)" },
        { transform: "translateY(0) scale(1)" }
      ],
      620
    );

    schedule(150, token, () => {
      animateElement(
        elements.trace,
        [
          { opacity: 0, strokeDashoffset: "0.56" },
          { offset: 0.12, opacity: 0.94, strokeDashoffset: "0" },
          { offset: 0.78, opacity: 0.88, strokeDashoffset: "-0.72" },
          { opacity: 0, strokeDashoffset: "-1" }
        ],
        { duration: 420, easing: "linear", fill: "forwards" }
      );
    });

    schedule(275, token, () => {
      const origin = {
        x: elements.card.clientWidth - 43,
        y: elements.card.clientHeight - 43
      };
      emitParticles(
        token,
        6,
        origin,
        [
          { x: -32, y: -38 },
          { x: -22, y: -58 },
          { x: -4, y: -47 },
          { x: 12, y: -66 },
          { x: 24, y: -34 },
          { x: 35, y: -52 }
        ],
        460
      );
      firePeakHaptic(token);
    });

    showBadge(token, false, 340);
  }

  function playAchievementStamp(token = state.runToken) {
    startFinishLineUpdate(
      token,
      [
        { transform: "translateY(0) scale(1)" },
        { offset: 0.4, transform: "translateY(-4px) scale(1)" },
        { transform: "translateY(0) scale(1)" }
      ],
      460
    );

    schedule(120, token, () => {
      elements.badge.setAttribute("aria-hidden", "false");
      animateElement(
        elements.badge,
        [
          { opacity: 0.12, transform: "translate(20px, 78px) scale(0.65) rotate(-6deg)" },
          { offset: 0.62, opacity: 1, transform: "translate(5px, 7px) scale(1.05) rotate(1deg)" },
          { opacity: 1, transform: "translate(0, 0) scale(1) rotate(0deg)" }
        ],
        { duration: 380, easing: settleEasing, fill: "forwards" }
      );
    });

    schedule(500, token, () => {
      const origin = {
        x: elements.card.clientWidth - 60,
        y: elements.card.clientHeight - 154
      };
      emitParticles(
        token,
        8,
        origin,
        [
          { x: -34, y: -26 },
          { x: -24, y: 18 },
          { x: -8, y: -42 },
          { x: 8, y: 30 },
          { x: 20, y: -34 },
          { x: 34, y: 14 },
          { x: -42, y: 2 },
          { x: 42, y: -12 }
        ],
        400
      );
      firePeakHaptic(token);
    });
  }

  function playReducedCompletion(token = state.runToken) {
    elements.fill.style.transform = "scaleX(1)";
    animateLabelCrossfade(token, true, 0);
    showBadge(token, true, 0);
    schedulePeakHaptic(160, token);
  }

  function playSelectedCelebration(token) {
    if (shouldReduceMotion()) {
      playReducedCompletion(token);
      return;
    }

    if (state.variant === "edge") {
      playEdgeSpark(token);
    } else if (state.variant === "stamp") {
      playAchievementStamp(token);
    } else {
      playFinishLine(token);
    }
  }

  function resetCompletion() {
    cancelRunningAnimations();
    state.progress = START_EPISODE;
    state.completed = false;
    state.hapticFired = false;
    renderProgress(state.progress);
    updateProgressAccessibility();
    updateDemoStatus();
    elements.snackbar.hidden = true;
  }

  function commitCompletion() {
    cancelRunningAnimations();
    state.progress = TOTAL_EPISODES;
    state.completed = true;
    state.hapticFired = false;
    updateProgressAccessibility();
    updateDemoStatus();
    elements.snackbar.hidden = false;
    announce("Starfall Academy completed. Undo is available.");

    const token = state.runToken;
    playSelectedCelebration(token);
  }

  function replay() {
    resetCompletion();
    commitCompletion();
  }

  function setRegularProgress(progress) {
    cancelRunningAnimations();
    state.progress = Math.max(0, Math.min(START_EPISODE, progress));
    state.completed = false;
    state.hapticFired = false;
    renderProgress(state.progress);
    updateProgressAccessibility();
    updateDemoStatus();
    elements.snackbar.hidden = true;
    announce(`Ready: Episode ${state.progress} of ${TOTAL_EPISODES}`);
  }

  function increaseProgress() {
    if (state.completed) {
      resetCompletion();
      commitCompletion();
      return;
    }

    if (state.progress < START_EPISODE) {
      setRegularProgress(state.progress + 1);
      return;
    }

    commitCompletion();
  }

  function decreaseProgress() {
    if (state.completed) {
      resetCompletion();
      setRegularProgress(START_EPISODE - 1);
      return;
    }

    setRegularProgress(state.progress - 1);
  }

  function endPress() {
    elements.plus.dataset.pressing = "false";
    activePointerId = null;
  }

  function setTheme(theme) {
    if (!['light', 'dark'].includes(theme)) return;

    state.theme = theme;
    root.dataset.theme = theme;
    setPressed(themeButtons, themeButtons.find((button) => button.dataset.themeChoice === theme));
  }

  function setMotion(motion) {
    if (!['full', 'reduced'].includes(motion)) return;

    state.motion = motion;
    root.dataset.motion = motion;
    setPressed(motionButtons, motionButtons.find((button) => button.dataset.motionChoice === motion));
    updateMotionNote();
  }

  function setVariant(variant) {
    if (!['finish', 'edge', 'stamp'].includes(variant)) return;

    state.variant = variant;
    root.dataset.variant = variant;
    setPressed(variantButtons, variantButtons.find((button) => button.dataset.variant === variant));
    resetCompletion();
    announce(`Ready: Episode ${START_EPISODE} of ${TOTAL_EPISODES}`);
  }

  function bindActivation(button, action) {
    let keyboardActionHandled = false;

    button.addEventListener("keydown", (event) => {
      if (event.repeat || (event.key !== "Enter" && event.key !== " ")) return;

      keyboardActionHandled = false;
      if (event.key === " ") event.preventDefault();
    });

    button.addEventListener("keyup", (event) => {
      if (event.key !== "Enter" && event.key !== " ") return;

      if (!keyboardActionHandled) {
        action();
        keyboardActionHandled = true;
      }
      window.setTimeout(() => {
        keyboardActionHandled = false;
      }, 0);
    });

    button.addEventListener("click", (event) => {
      if (event.detail !== 0) {
        action();
        return;
      }

      // Depending on the browser, a keyboard click can arrive before or after
      // keyup. This keeps keyboard activation at exactly one action.
      if (keyboardActionHandled) {
        keyboardActionHandled = false;
        return;
      }

      action();
      keyboardActionHandled = true;
      window.setTimeout(() => {
        keyboardActionHandled = false;
      }, 0);
    });
  }

  function bindPlusButton() {
    elements.plus.addEventListener("pointerdown", (event) => {
      if (event.button !== undefined && event.button !== 0) return;

      activePointerId = event.pointerId;
      elements.plus.dataset.pressing = "true";
      if (typeof elements.plus.setPointerCapture === "function") {
        elements.plus.setPointerCapture(event.pointerId);
      }
    });

    elements.plus.addEventListener("pointerup", (event) => {
      if (activePointerId !== null && event.pointerId !== activePointerId) return;

      endPress();
      increaseProgress();
    });

    elements.plus.addEventListener("pointercancel", (event) => {
      if (activePointerId === null || event.pointerId === activePointerId) endPress();
    });

    elements.plus.addEventListener("lostpointercapture", () => {
      if (activePointerId !== null) endPress();
    });

    elements.plus.addEventListener("keydown", (event) => {
      if (event.key === "Enter" || event.key === " ") {
        keyboardPressActive = true;
        keyboardClickObserved = false;
        keyboardCommitHandled = false;
        elements.plus.dataset.pressing = "true";
      }
    });

    elements.plus.addEventListener("keyup", (event) => {
      if (event.key === "Enter" || event.key === " ") {
        endPress();
        keyboardPressActive = false;

        // Some browsers dispatch a keyboard click before keyup, while others
        // only deliver keyup in this test surface. Commit exactly once in both.
        if (!keyboardClickObserved && !keyboardCommitHandled) {
          increaseProgress();
          keyboardCommitHandled = true;
        }

        window.setTimeout(() => {
          keyboardClickObserved = false;
          keyboardCommitHandled = false;
        }, 0);
      }
    });

    // Pointer activation commits on pointer-up. Keyboard and assistive technology
    // activation arrives as a click with detail 0.
    elements.plus.addEventListener("click", (event) => {
      if (event.detail !== 0) return;

      if (keyboardPressActive) {
        keyboardClickObserved = true;
      }

      if (!keyboardCommitHandled) {
        increaseProgress();
        keyboardCommitHandled = true;
      }
    });
  }

  bindActivation(elements.undo, () => {
    if (!state.completed) return;
    resetCompletion();
    announce("Starfall Academy completion undone. Ready: Episode 11 of 12.");
  });

  bindActivation(elements.replay, replay);
  bindActivation(elements.minus, decreaseProgress);
  bindPlusButton();

  variantButtons.forEach((button) => {
    bindActivation(button, () => setVariant(button.dataset.variant));
  });

  themeButtons.forEach((button) => {
    bindActivation(button, () => setTheme(button.dataset.themeChoice));
  });

  motionButtons.forEach((button) => {
    bindActivation(button, () => setMotion(button.dataset.motionChoice));
  });

  navButtons.forEach((button) => {
    bindActivation(button, () => {
      const destination = button.querySelector(".nav-label")?.textContent || "Navigation";
      if (!button.classList.contains("is-active")) {
        announce(`${destination} is preview-only in this comparison.`);
      }
    });
  });

  const handleSystemMotionChange = () => {
    root.dataset.systemReduced = String(systemMotion.matches);
    updateMotionNote();
  };

  if (typeof systemMotion.addEventListener === "function") {
    systemMotion.addEventListener("change", handleSystemMotionChange);
  } else if (typeof systemMotion.addListener === "function") {
    systemMotion.addListener(handleSystemMotionChange);
  }

  root.dataset.variant = state.variant;
  root.dataset.systemReduced = String(systemMotion.matches);
  renderProgress(state.progress);
  updateProgressAccessibility();
  updateDemoStatus();
  updateMotionNote();
})();
