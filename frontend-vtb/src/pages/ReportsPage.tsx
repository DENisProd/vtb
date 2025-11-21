import {
  Button,
  Card,
  CardBody,
  CardHeader,
  Chip,
  Input,
  Select,
  SelectItem,
} from "@heroui/react";
import {
  DocumentArrowDownIcon,
  DocumentChartBarIcon,
} from "@heroicons/react/24/outline";

const formats = [
  { key: "pdf", label: "PDF" },
  { key: "html", label: "HTML" },
  { key: "json", label: "JSON" },
];

const ReportsPage = () => (
  <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
    <Card className="border border-white/10 bg-white/5 lg:col-span-2">
      <CardHeader className="items-start gap-3">
        <div className="rounded-full bg-primary/10 p-2">
          <DocumentChartBarIcon className="h-5 w-5 text-primary" />
        </div>
        <div>
          <div className="text-sm font-semibold text-white">
            Отчёты по прогонам
          </div>
          <div className="text-xs text-slate-400">
            SLA, тайминги, ошибки, экспорт в PDF/HTML
          </div>
        </div>
      </CardHeader>
      <CardBody className="space-y-4">
        <div className="grid grid-cols-2 gap-3">
          <Select defaultSelectedKeys={["pdf"]} label="Формат">
            {formats.map((format) => (
              <SelectItem key={format.key}>{format.label}</SelectItem>
            ))}
          </Select>
          <Input label="Сценарий" placeholder="ID сценария или *" />
        </div>
        <Button
          color="primary"
          startContent={<DocumentArrowDownIcon className="h-4 w-4" />}
        >
          Сгенерировать отчёт
        </Button>

        <div className="rounded-2xl border border-white/10 p-4">
          <div className="text-sm font-semibold text-white">
            Последние отчёты
          </div>
          <div className="mt-2 space-y-2">
            {["14:32", "12:18", "Вчера"].map((time, index) => (
              <div
                key={time}
                className="flex items-center justify-between rounded-xl border border-white/10 px-3 py-2"
              >
                <div>
                  <div className="text-sm text-white">Прогон #{index + 41}</div>
                  <div className="text-xs text-slate-400">Создано {time}</div>
                </div>
                <Chip size="sm" variant="flat">
                  PDF
                </Chip>
              </div>
            ))}
          </div>
        </div>
      </CardBody>
    </Card>

    <Card className="border border-white/10">
      <CardHeader>
        <div>
          <div className="text-sm font-semibold text-white">
            Экспорт сценариев
          </div>
          <div className="text-xs text-slate-400">JSON, Postman, CSV данных</div>
        </div>
      </CardHeader>
      <CardBody className="space-y-3">
        <Button variant="flat">Экспорт сценариев (JSON)</Button>
        <Button variant="flat">Postman коллекция</Button>
        <Button variant="flat">Данные (CSV)</Button>
      </CardBody>
    </Card>
  </div>
);

export default ReportsPage;

