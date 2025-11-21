import { useEffect, useState } from "react";
import { Button } from "@heroui/react";
import { MoonIcon, SunIcon } from "@heroicons/react/24/solid";

const STORAGE_KEY = "orchestra-theme";

const getInitialTheme = () => {
  if (typeof window === "undefined") return "dark";
  const saved = window.localStorage.getItem(STORAGE_KEY);

  if (saved === "light" || saved === "dark") {
    return saved;
  }

  return window.matchMedia("(prefers-color-scheme: dark)").matches
    ? "dark"
    : "light";
};

export const ThemeSwitch = () => {
  const [theme, setTheme] = useState<"light" | "dark">(getInitialTheme);

  useEffect(() => {
    if (typeof window === "undefined") return;

    const root = document.documentElement;
    root.dataset.theme = theme;
    document.body.dataset.theme = theme;
    window.localStorage.setItem(STORAGE_KEY, theme);
  }, [theme]);

  return (
    <Button
      isIconOnly
      aria-label="Переключить тему"
      className="btn-ghost !p-2"
      radius="lg"
      variant="flat"
      onPress={() => setTheme((prev) => (prev === "dark" ? "light" : "dark"))}
    >
      {theme === "dark" ? (
        <SunIcon className="h-5 w-5 text-[var(--app-on-primary)]" />
      ) : (
        <MoonIcon className="h-5 w-5 text-[var(--app-primary)]" />
      )}
    </Button>
  );
};

