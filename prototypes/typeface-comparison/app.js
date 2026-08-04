(() => {
  "use strict";

  const root = document.documentElement;
  const deviceMain = document.querySelector("#device-main");
  const fontStatus = document.querySelector("#font-status");
  const fontButtons = [...document.querySelectorAll("button[data-font]")];
  const screenButtons = [...document.querySelectorAll("button[data-screen]")];
  const themeButtons = [...document.querySelectorAll("[data-theme-choice]")];
  const screenPanels = [...document.querySelectorAll("[data-panel]")];
  const navButtons = [...document.querySelectorAll("[data-nav-screen]")];
  const filterButtons = [...document.querySelectorAll("[data-filter-group]")];
  const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");

  const fonts = {
    gabarito: '"Gabarito"',
    bricolage: '"Bricolage Grotesque"',
    onest: '"Onest"'
  };
  const fontWeights = [400, 500, 600, 700];
  let screenSwitchToken = 0;

  function setPressed(buttons, activeButton) {
    buttons.forEach((button) => {
      const isActive = button === activeButton;
      button.classList.toggle("is-active", isActive);
      button.setAttribute("aria-pressed", String(isActive));
    });
  }

  function setFont(fontKey) {
    if (!fonts[fontKey]) return;

    // The preview variable is the only app-level visual value changed by this control.
    root.style.setProperty("--active-font", fonts[fontKey]);
    root.dataset.font = fontKey;
    const activeButton = fontButtons.find((button) => button.dataset.font === fontKey);
    if (activeButton) setPressed(fontButtons, activeButton);
  }

  function updateScreenPanels(screen) {
    screenPanels.forEach((panel) => {
      const isActive = panel.dataset.panel === screen;
      panel.hidden = !isActive;
      panel.classList.toggle("is-active", isActive);
    });

    navButtons.forEach((button) => {
      const isActive = button.dataset.navScreen === screen;
      button.classList.toggle("is-active", isActive);
      if (isActive) {
        button.setAttribute("aria-current", "page");
      } else {
        button.removeAttribute("aria-current");
      }
    });
  }

  function setScreen(screen) {
    if (!["today", "library"].includes(screen)) return;
    const currentScreen = root.dataset.screen;
    if (currentScreen === screen) return;

    const token = ++screenSwitchToken;
    const duration = reducedMotion.matches ? 0 : 140;
    deviceMain.classList.add("is-switching");

    window.setTimeout(() => {
      if (token !== screenSwitchToken) return;
      root.dataset.screen = screen;
      updateScreenPanels(screen);
      setPressed(screenButtons, screenButtons.find((button) => button.dataset.screen === screen));
      window.requestAnimationFrame(() => deviceMain.classList.remove("is-switching"));
    }, duration);
  }

  function setTheme(theme) {
    if (!["light", "dark"].includes(theme)) return;
    root.dataset.theme = theme;
    setPressed(themeButtons, themeButtons.find((button) => button.dataset.themeChoice === theme));
  }

  function setFilter(group, activeButton) {
    filterButtons
      .filter((button) => button.dataset.filterGroup === group)
      .forEach((button) => {
        const isActive = button === activeButton;
        button.classList.toggle("is-active", isActive);
        button.setAttribute("aria-pressed", String(isActive));
      });
  }

  async function verifyFonts() {
    const fontLoads = Object.values(fonts).flatMap((fontFamily) =>
      fontWeights.map((weight) => document.fonts.load(`${weight} 16px ${fontFamily}`))
    );

    try {
      await Promise.all(fontLoads);
      await document.fonts.ready;
    } catch (error) {
      // document.fonts.check below remains the source of truth for the warning.
    }

    const missingFonts = Object.entries(fonts)
      .filter(([, fontFamily]) =>
        fontWeights.some((weight) => !document.fonts.check(`${weight} 16px ${fontFamily}`))
      )
      .map(([fontKey]) => fontKey);

    if (missingFonts.length) {
      fontStatus.dataset.state = "warning";
      fontStatus.textContent = `Warning: ${missingFonts.join(", ")} failed to load. Fallback text is active.`;
      return;
    }

    fontStatus.dataset.state = "ready";
    fontStatus.textContent = "Google Fonts loaded · all four comparison weights checked";
  }

  fontButtons.forEach((button) => {
    button.addEventListener("click", () => setFont(button.dataset.font));
  });

  screenButtons.forEach((button) => {
    button.addEventListener("click", () => setScreen(button.dataset.screen));
  });

  themeButtons.forEach((button) => {
    button.addEventListener("click", () => setTheme(button.dataset.themeChoice));
  });

  navButtons.forEach((button) => {
    button.addEventListener("click", () => {
      if (button.dataset.navScreen) setScreen(button.dataset.navScreen);
    });
  });

  filterButtons.forEach((button) => {
    button.addEventListener("click", () => setFilter(button.dataset.filterGroup, button));
  });

  updateScreenPanels(root.dataset.screen || "today");
  verifyFonts();
})();
