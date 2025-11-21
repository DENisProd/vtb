import {
  Card,
  CardBody,
  CardHeader,
  Chip,
  Input,
  Select,
  SelectItem,
  Switch,
  Textarea,
} from "@heroui/react";
import {
  AdjustmentsHorizontalIcon,
  ShieldCheckIcon,
} from "@heroicons/react/24/outline";

const authOptions = [
  { key: "oidc", label: "OIDC" },
  { key: "saml", label: "SAML" },
];

const SettingsPage = () => (
  <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
    <Card className="border border-white/10 bg-white/5">
      <CardHeader className="items-start gap-3">
        <div className="rounded-full bg-secondary/10 p-2">
          <AdjustmentsHorizontalIcon className="h-5 w-5 text-secondary" />
        </div>
        <div>
          <div className="text-sm font-semibold text-white">
            Интеграции и очереди
          </div>
          <div className="text-xs text-slate-400">вебхуки, CI/CD, трекер задач</div>
        </div>
      </CardHeader>
      <CardBody className="space-y-3">
        <Input
          label="URL вебхука"
          placeholder="https://ci.example.com/hook"
        />
        <Select label="Трекер задач">
          <SelectItem key="jira">Jira</SelectItem>
          <SelectItem key="youtrack">YouTrack</SelectItem>
          <SelectItem key="linear">Linear</SelectItem>
        </Select>
        <Textarea label="Описание задачи по умолчанию" minRows={3} />
        <Switch defaultSelected color="primary">
          Автоматически создавать задачи для ошибок
        </Switch>
      </CardBody>
    </Card>

    <Card className="border border-white/10">
      <CardHeader className="items-start gap-3">
        <div className="rounded-full bg-success/10 p-2">
          <ShieldCheckIcon className="h-5 w-5 text-success" />
        </div>
        <div>
          <div className="text-sm font-semibold text-white">
            Доступ и ограничения
          </div>
          <div className="text-xs text-slate-400">RBAC, лимиты на прогоны, безопасность</div>
        </div>
      </CardHeader>
      <CardBody className="space-y-3">
        <Select label="Провайдер авторизации" selectedKeys={["oidc"]}>
          {authOptions.map((option) => (
            <SelectItem key={option.key}>{option.label}</SelectItem>
          ))}
        </Select>
        <Input
          defaultValue="5"
          label="Максимальное число параллельных прогонов"
          type="number"
        />
        <Switch defaultSelected>Авто-пауза при ошибках SLA</Switch>
        <div className="rounded-xl border border-white/10 p-3">
          <div className="text-xs uppercase text-slate-500">Роли</div>
          <div className="mt-2 flex gap-2">
            {["Тестировщик", "Аналитик", "Инженер", "Админ"].map((role) => (
              <Chip key={role} variant="flat">
                {role}
              </Chip>
            ))}
          </div>
        </div>
      </CardBody>
    </Card>
  </div>
);

export default SettingsPage;

