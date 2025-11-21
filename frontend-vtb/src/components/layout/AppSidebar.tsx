import type { ElementType } from "react";

import { useLocation, useNavigate } from "react-router-dom";
import { Button, Tooltip } from "@heroui/react";
import {
  AdjustmentsHorizontalIcon,
  ArrowDownTrayIcon,
  ArrowPathIcon,
  BeakerIcon,
  ChartBarIcon,
  Cog8ToothIcon,
  DocumentChartBarIcon,
  QueueListIcon,
  Squares2X2Icon,
  SwatchIcon,
} from "@heroicons/react/24/outline";

import Logo from "./Logo.svg"

import { siteConfig } from "@/config/site";

const iconMap: Record<string, ElementType> = {
  Обзор: ChartBarIcon,
  Артефакты: ArrowDownTrayIcon,
  Аналитика: BeakerIcon,
  Сценарии: QueueListIcon,
  Данные: SwatchIcon,
  Прогоны: ArrowPathIcon,
  Канва: Squares2X2Icon,
  Отчёты: DocumentChartBarIcon,
  Проекты: DocumentChartBarIcon,
  Настройки: Cog8ToothIcon,
};

export const AppSidebar = () => {
  const navigate = useNavigate();
  const location = useLocation();
  return (
  <aside className="hidden w-64 shrink-0 flex-col border-r border-white/10 bg-[var(--app-bg-alt)]/70 backdrop-blur-xl md:flex">
    <div className="border-b border-white/10 px-6 py-5">
      <div className="flex items-center gap-3">
            <img
              alt="Логотип ВТБ"
              className="h-6 w-auto"
              src={Logo}
            />
            <div className="text-sm uppercase tracking-wide text-muted">
              По IB
            </div>
          </div>
    </div>

    <nav className="flex-1 space-y-1 overflow-y-auto px-3 py-4">
      {siteConfig.navItems.map((item) => {
        const Icon = iconMap[item.label] ?? AdjustmentsHorizontalIcon;
        const isActive = location.pathname === item.href;
        return (
          <Tooltip
            key={item.href}
            className="md:hidden"
            content={item.label}
            placement="right"
          >
            <Button
              className={`nav-button ${
                isActive ? "nav-button--active" : "nav-button--idle"
              }`}
              size="lg"
              startContent={<Icon className="h-4 w-4" />}
              variant="light"
              onPress={() => navigate(item.href)}
            >
              {item.label}
            </Button>
          </Tooltip>
        );
      })}
    </nav>
  </aside>
  );
};

