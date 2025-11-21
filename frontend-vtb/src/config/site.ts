export const siteConfig = {
  name: "Orchestra Автоматизация тестов",
  description:
    "Конструктор тестовых прогонов по артефактам BPMN и OpenAPI с AI-помощником.",
  navItems: [
    { label: "Проекты", href: "/projects" },
    { label: "Избранное", href: "/favorites" },
    { label: "Настройки", href: "/settings" },
  ],
};

export type SiteConfig = typeof siteConfig;

