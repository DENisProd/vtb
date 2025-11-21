# Orchestra Frontend (VTB edition)

UI сервис визуального конструктора прогонов, перенесённый из `infra-constructor` в облегчённое SPA на React + Vite. Интерфейс ориентирован на тестировщиков и аналитиков: подключение артефактов (BPMN/OpenAPI), анализ несогласованностей, визуальный редактор сценариев, генератор тестовых данных, раннер с live логами и канвас-проигрывателем.

## Стек

- React 18 + TypeScript + Vite
- HeroUI (NextUI v2) + Tailwind CSS 4
- Zustand (persist в localStorage) для артефактов/сценариев/прогонов
- react-konva для канваса BPMN/API
- Websocket-ready заглушки (`lib/testflow-api.ts`) для подключения к backend

## Быстрый старт

```bash
npm install
npm run dev
```

По умолчанию фронт обращается к `http://localhost:8080` (`VITE_API_BASE_URL`) для:

- `POST /api/mapping/map` — сопоставление BPMN+OpenAPI
- `POST /api/runner/run` — запуск прогона сценария
- `GET /api/runner/:id` / `WS /ws/runner/:id` — статусы и логи

Переменную можно переопределить через `.env`.

## Основные разделы

- **Dashboard** — обзор артефактов, сценариев, AI-инсайтов, статусов раннера
- **Artifacts** — загрузка BPMN/OpenAPI/.md и подключение репозитория/CI
- **Analysis** — NLP-issues с фильтрами, комментариями и назначениями
- **Scenarios** — визуальный редактор цепочек + drag/drop-заготовка, подробности шага
- **Data** — генератор тестовых данных с зависимостями, seed шаблоны
- **Runner** — очередь прогонов, live-логи, экспорт payload’ов
- **Canvas** — react-konva канвас, playback по шагам, логи на каждый узел
- **Reports/Settings** — экспорт отчетов, интеграции, лимиты, RBAC-заглушки

Все данные сохраняются локально — после перезагрузки состояние восстанавливается из `localStorage`.

## Скрипты

- `npm run dev` — локальная разработка
- `npm run build` — production-сборка
- `npm run preview` — предпросмотр сборки
- `npm run lint` — ESLint
- `npm run typecheck` — `tsc --noEmit`

## Следующие шаги

- Подключить реальные вызовы backend + вебсокеты
- Добавить проверку JSON Schema (по ссылкам из OpenAPI)
- Настроить авторизацию (OIDC/SAML) — сейчас UI работает без логина
