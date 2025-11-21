import { useState, useMemo, useEffect, useRef } from "react";
import { Button, Card, CardBody, CardHeader, Chip, Tabs, Tab, Select, SelectItem, Input } from "@heroui/react";
import { ExclamationTriangleIcon } from "@heroicons/react/24/outline";
import Editor from "@monaco-editor/react";
import { ProjectDto, remapProject } from "@/lib/testflow-api";
import { Arrow, Circle, Group, Layer, Rect, Stage, Text } from "react-konva";

interface ProjectArtifactsTabProps {
  project: ProjectDto;
  requireHint?: boolean;
}

function BpmnVisualization({ xml }: { xml: string }) {
  const doc = useMemo(() => new DOMParser().parseFromString(xml, "text/xml"), [xml]);
  const taskNodes = useMemo(() => Array.from(doc.querySelectorAll("bpmn\\:task, task")), [doc]);
  const gatewayNodes = useMemo(() => Array.from(doc.querySelectorAll("bpmn\\:exclusiveGateway, exclusiveGateway, bpmn\\:gateway, gateway")), [doc]);
  const eventNodes = useMemo(() => Array.from(doc.querySelectorAll("bpmn\\:startEvent, bpmn\\:endEvent, startEvent, endEvent")), [doc]);
  const [scale, setScale] = useState<number>(1);
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [dimensions, setDimensions] = useState({ width: 800, height: 500 });

  useEffect(() => {
    const resize = () => {
      if (!containerRef.current) return;
      setDimensions({
        width: containerRef.current.clientWidth,
        height: Math.max(420, containerRef.current.clientHeight),
      });
    };
    resize();
    window.addEventListener("resize", resize);
    return () => window.removeEventListener("resize", resize);
  }, []);

  const boxW = 220;
  const boxH = 64;
  const gapX = 100;
  const startX = 140;
  const y = 160;
  const positions = useMemo(() => taskNodes.map((_, idx) => ({ x: startX + idx * (boxW + gapX), y })), [taskNodes]);

  const getPoints = (fromIdx: number, toIdx: number) => {
    const f = positions[fromIdx];
    const t = positions[toIdx];
    const x1 = f.x + boxW;
    const y1 = f.y + boxH / 2;
    const x2 = t.x;
    const y2 = t.y + boxH / 2;
    return [x1, y1, x2, y2];
  };

  return (
    <div className="h-full overflow-auto p-4">
      <div className="flex items-center gap-3 mb-3">
        <div className="text-xs text-muted">Масштаб</div>
        <input type="range" min={0.5} max={2} step={0.1} value={scale} onChange={(e) => setScale(parseFloat(e.target.value))} />
        <div className="text-xs text-muted">{Math.round(scale * 100)}%</div>
      </div>
      <div ref={containerRef} className="relative min-h-[420px] canvas-grid-bg rounded-xl border border-white/10">
        <Stage height={dimensions.height} width={dimensions.width} scaleX={scale} scaleY={scale} style={{ pointerEvents: "none" }}>
          <Layer>
            {eventNodes.length > 0 && (
              <Group x={startX - 20} y={y + boxH / 2 - 10} listening={false}>
                <Circle radius={12} strokeWidth={4} stroke="#22c55e" fill="#ffffff" />
              </Group>
            )}
            {positions.map((_, idx) => {
              if (idx >= positions.length - 1) return null;
              const [sx, sy, ex, ey] = getPoints(idx, idx + 1);
              return <Arrow key={`arrow-${idx}`} points={[sx, sy, ex, ey]} stroke="#3b82f6" fill="#3b82f6" strokeWidth={2} pointerLength={10} pointerWidth={10} listening={false} />;
            })}
            {eventNodes.length > 0 && positions.length > 0 && (
              <Arrow points={[startX - 36, y + boxH / 2, positions[0].x, positions[0].y + boxH / 2]} stroke="#3b82f6" fill="#3b82f6" strokeWidth={2} pointerLength={10} pointerWidth={10} listening={false} />
            )}
            {eventNodes.length > 1 && positions.length > 0 && (
              <Group>
                <Arrow points={[positions[positions.length - 1].x + boxW, y + boxH / 2, positions[positions.length - 1].x + boxW + 80, y + boxH / 2]} stroke="#3b82f6" fill="#3b82f6" strokeWidth={2} pointerLength={10} pointerWidth={10} listening={false} />
                <Group x={positions[positions.length - 1].x + boxW + 80} y={y + boxH / 2} listening={false}>
                  <Circle radius={12} strokeWidth={4} stroke="#ef4444" fill="#ffffff" />
                </Group>
              </Group>
            )}
            {taskNodes.map((task, idx) => {
              const name = task.getAttribute("name") || `Task ${idx + 1}`;
              const p = positions[idx];
              return (
                <Group key={`task-${idx}`} x={p.x + boxW / 2} y={p.y + boxH / 2} listening={false}>
                  <Rect cornerRadius={12} height={boxH} width={boxW} offset={{ x: boxW / 2, y: boxH / 2 }} fill="#eff6ff" stroke="#3b82f6" strokeWidth={2} />
                  <Text align="center" fill="#0f172a" fontSize={12} fontStyle="bold" offset={{ x: boxW / 2, y: boxH / 2 - 6 }} text={name} width={boxW - 16} wrap="word" />
                </Group>
              );
            })}
            {gatewayNodes.map((_, idx) => {
              const x = startX + (taskNodes.length + idx) * (boxW + gapX);
              return (
                <Group key={`gw-${idx}`} x={x + 32} y={y + 32} rotation={45} listening={false}>
                  <Rect height={64} width={64} offset={{ x: 32, y: 32 }} fill="#f5f3ff" stroke="#a855f7" strokeWidth={2} />
                </Group>
              );
            })}
          </Layer>
        </Stage>
      </div>
    </div>
  );
}

function OpenApiVisualization({ json }: { json: string }) {
  let parsed: any = {};
  try {
    parsed = JSON.parse(json);
  } catch {
    return <div className="text-muted">Неверный JSON</div>;
  }

  const paths = parsed.paths || {};
  const servers = parsed.servers || [];

  return (
    <div className="h-full overflow-auto p-4 space-y-4">
      {servers.length > 0 && (
        <div>
          <div className="text-sm font-semibold text-[var(--app-text)] mb-2">Серверы</div>
          <div className="space-y-1">
            {servers.map((server: any, idx: number) => (
              <div key={idx} className="text-xs text-muted">
                {server.url}
              </div>
            ))}
          </div>
        </div>
      )}

      <div>
        <div className="text-sm font-semibold text-[var(--app-text)] mb-2">Эндпоинты</div>
        <div className="space-y-3">
          {Object.entries(paths).map(([path, methods]: [string, any]) => (
            <div key={path} className="border border-white/10 rounded-lg p-3 bg-white/5">
              <div className="font-mono text-sm text-[var(--app-text)] mb-2 break-all">{path}</div>
              <div className="space-y-2">
                {Object.entries(methods).map(([method, details]: [string, any]) => (
                  <div key={method} className="flex items-center gap-2">
                    <Chip
                      size="sm"
                      variant="flat"
                      color={
                        method === "get"
                          ? "primary"
                          : method === "post"
                            ? "success"
                            : method === "put"
                              ? "warning"
                              : "danger"
                      }
                    >
                      {method.toUpperCase()}
                    </Chip>
                    <div className="text-xs text-muted break-words">
                      {details.summary || details.operationId || "—"}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function PumlVisualization({ content }: { content: string }) {
  const lines = content.split("\n").filter((line) => line.trim());
  const actors = lines.filter((line) => line.includes("actor") || line.includes("participant"));
  const messages = lines.filter((line) => line.includes("->") || line.includes("-->"));

  return (
    <div className="h-full overflow-auto p-4">
      <div className="space-y-4">
        {actors.length > 0 && (
          <div>
            <div className="text-sm font-semibold text-[var(--app-text)] mb-2">Участники</div>
            <div className="space-y-1">
              {actors.map((actor, idx) => (
                <div key={idx} className="text-xs text-muted">
                  {actor.replace(/actor|participant|\s+/g, " ").trim()}
                </div>
              ))}
            </div>
          </div>
        )}

        {messages.length > 0 && (
          <div>
            <div className="text-sm font-semibold text-[var(--app-text)] mb-2">Сообщения</div>
            <div className="space-y-2">
              {messages.map((msg, idx) => (
                <div key={idx} className="border border-white/10 rounded-lg p-2 bg-white/5">
                  <div className="font-mono text-xs text-[var(--app-text)]">{msg.trim()}</div>
                </div>
              ))}
            </div>
          </div>
        )}

        {actors.length === 0 && messages.length === 0 && (
          <div className="text-muted">
            <pre className="text-xs whitespace-pre-wrap">{content}</pre>
          </div>
        )}
      </div>
    </div>
  );
}

export function ProjectArtifactsTab({ project, requireHint }: ProjectArtifactsTabProps) {
  const [viewMode, setViewMode] = useState<"code" | "visual">("visual");
  const [selectedArtifact, setSelectedArtifact] = useState<string | null>(null);
  const [uploadedPuml, setUploadedPuml] = useState<string | null>(null);
  const [uploadedBpmn, setUploadedBpmn] = useState<string | null>(null);
  const [uploadedOpenApi, setUploadedOpenApi] = useState<string | null>(null);
  const [addOpen, setAddOpen] = useState<boolean>(false);
  const [addType, setAddType] = useState<"openapi" | "bpmn" | "puml">("openapi");
  const [droppedFileName, setDroppedFileName] = useState<string | null>(null);
  const [droppedContent, setDroppedContent] = useState<string | null>(null);
  const [openApiUrl, setOpenApiUrl] = useState<string>("");
  const [openApiUrlError, setOpenApiUrlError] = useState<string>("");
  const [adding, setAdding] = useState<boolean>(false);

  if (requireHint && !addOpen) {
    // Авто-открыть добавление при требовании артефактов
    setAddOpen(true);
  }

  const artifacts = [
    (uploadedBpmn || project.bpmnXml) && { type: "bpmn", name: "BPMN диаграмма", content: uploadedBpmn || project.bpmnXml },
    (uploadedOpenApi || project.openApiJson) && {
      type: "openapi",
      name: "OpenAPI спецификация",
      content: uploadedOpenApi || project.openApiJson,
    },
    (uploadedPuml || project.pumlContent) && {
      type: "puml",
      name: "PUML схема",
      content: uploadedPuml || project.pumlContent,
    },
    uploadedPuml && {
      type: "puml",
      name: "PUML схема (добавлено)",
      content: uploadedPuml,
    },
  ].filter(Boolean) as Array<{
    type: string;
    name: string;
    content: string;
  }>;

  const currentArtifact =
    artifacts.find((a) => a.type === selectedArtifact) ??
    (artifacts.length > 0 ? artifacts[0] : null);

  return (
    <div className="flex gap-4 h-full min_h-[500px]">
      <div className="w-64 shrink-0 app-card p-4 flex flex-col">
        <div className="text-sm font-semibold text-[var(--app-text)] mb-3">
          Процессы
        </div>
        {requireHint && (
          <div className="mb-2 flex items-center gap-2 rounded-lg border border-warning/40 bg-warning/20 p-2 text-[11px] font-medium text-warning">
            <ExclamationTriangleIcon className="w-4 h-4" />
            <span>Добавьте OpenAPI и BPMN/PUML, чтобы запустить анализ</span>
          </div>
        )}
        <div className="flex-1 min-h-0 overflow-y-auto space-y-2">
          {artifacts.map((artifact) => (
            <button
              key={artifact.name}
              className={`w-full text-left rounded-lg border px-3 py-2 transition-colors ${
                selectedArtifact === artifact.type || (!selectedArtifact && artifacts.indexOf(artifact) === 0)
                  ? "border-primary/60 bg-primary/10"
                  : "border-white/10 hover:bg-white/5 hover:border-white/20"
              }`}
              onClick={() => setSelectedArtifact(artifact.type)}
              type="button"
            >
              <div className="flex items-center justify-between gap-2">
                <div className="text-xs font-semibold text-[var(--app-text)] truncate">{artifact.name}</div>
                <Chip size="sm" variant="flat" className="shrink-0">{artifact.type.toUpperCase()}</Chip>
              </div>
            </button>
          ))}
        </div>
        <div className="pt-3 mt-3 border-t border-white/10 shrink-0 space-y-2">
          {!addOpen ? (
            <Button size="sm" variant="bordered" className="w-full" onPress={() => setAddOpen(true)}>
              Добавить
            </Button>
          ) : (
            <div className="space-y-2 rounded-lg border border-primary/50 bg-primary/5 p-3">
              <Select 
                size="sm" 
                selectedKeys={[addType]} 
                onSelectionChange={(keys) => { const k = Array.from(keys)[0] as string; setAddType((k as any) || "openapi"); setDroppedContent(null); setDroppedFileName(null); }}
                label="Тип процесса"
              >
                <SelectItem key="openapi">OpenAPI</SelectItem>
                <SelectItem key="bpmn">BPMN</SelectItem>
                <SelectItem key="puml">PUML</SelectItem>
              </Select>

              {addType === "openapi" && (
                <Input 
                  size="sm" 
                  label="URL JSON" 
                  placeholder="https://example.com/openapi.json" 
                  value={openApiUrl}
                  onValueChange={(val) => {
                    setOpenApiUrl(val);
                    const ok = /\.json(?:\?|#|$)/i.test(val.trim());
                    setOpenApiUrlError(ok || !val.trim() ? "" : "URL должен заканчиваться на .json");
                  }}
                  errorMessage={openApiUrlError || undefined}
                />
              )}

              <div
                className="rounded-lg border border-dashed border-primary/60 bg-primary/5 p-5 text-center text-xs cursor-pointer hover:bg-primary/10 hover:border-primary/70 shadow-sm"
                onClick={() => {
                  const input = document.createElement("input");
                  input.type = "file";
                  input.accept = addType === "bpmn" ? ".xml" : addType === "openapi" ? ".json" : ".puml";
                  input.onchange = (e: any) => {
                    const file = e.target.files?.[0];
                    if (!file) return;
                    setDroppedFileName(file.name);
                    const reader = new FileReader();
                    reader.onload = (ev) => setDroppedContent((ev.target?.result as string) || "");
                    reader.readAsText(file);
                  };
                  input.click();
                }}
                onDragOver={(e) => { e.preventDefault(); }}
                onDrop={(e) => {
                  e.preventDefault();
                  const file = e.dataTransfer.files?.[0];
                  if (!file) return;
                  setDroppedFileName(file.name);
                  const reader = new FileReader();
                  reader.onload = (ev) => setDroppedContent((ev.target?.result as string) || "");
                  reader.readAsText(file);
                }}
              >
                {droppedFileName ? (
                  <div className="text-[11px] font-medium text-[var(--app-text)]">{droppedFileName}</div>
                ) : (
                  <div className="space-y-1">
                    <div className="text-[var(--app-text)]">Перетащите файл сюда или нажмите для выбора</div>
                    <div className="text-[10px] text-muted">Тип: {addType === "openapi" ? "JSON" : addType === "bpmn" ? "XML" : "PUML"}</div>
                  </div>
                )}
              </div>

              <div className="flex gap-2">
                <Button 
                  size="sm" 
                  className="btn-primary flex-1" 
                  isDisabled={addType === "openapi" ? (!droppedContent && !openApiUrl.trim()) || !!openApiUrlError : !droppedContent}
                  isLoading={adding}
                  onPress={async () => {
                    try {
                      setAdding(true);
                      let content = droppedContent;
                      if (addType === "openapi" && !content && openApiUrl.trim()) {
                        const res = await fetch(openApiUrl.trim());
                        if (!res.ok) throw new Error("Не удалось загрузить JSON по URL");
                        content = await res.text();
                      }
                      if (!content) return;
                      if (addType === "bpmn") {
                        setUploadedBpmn(content);
                        await remapProject(project.id, { bpmnXml: content });
                      } else if (addType === "openapi") {
                        setUploadedOpenApi(content);
                        await remapProject(project.id, { openApiJson: content });
                      } else {
                        setUploadedPuml(content);
                        await remapProject(project.id, { pumlContent: content });
                      }
                      setAddOpen(false);
                      setDroppedContent(null);
                      setDroppedFileName(null);
                      setOpenApiUrl("");
                      setOpenApiUrlError("");
                    } catch (e) {
                      console.error(e);
                    } finally {
                      setAdding(false);
                    }
                  }}
                >
                  Добавить процесс
                </Button>
                <Button size="sm" variant="light" onPress={() => { setAddOpen(false); setDroppedContent(null); setDroppedFileName(null); setOpenApiUrl(""); setOpenApiUrlError(""); }}>
                  Отмена
                </Button>
              </div>
            </div>
          )}
        </div>
      </div>

      <div className="flex-1 app-card p-4 canvas-grid-bg flex flex-col min-h-0 min-w-0">
        {currentArtifact ? (
          <>
            <div className="flex items-center justify-between mb-3 shrink-0">
              <div className="text-base font-semibold text-[var(--app-text)] truncate">
                {currentArtifact.name}
              </div>
              <Tabs
                size="sm"
                selectedKey={viewMode}
                onSelectionChange={(key) => setViewMode(key as "code" | "visual")}
                classNames={{
                  tabList: "gap-1",
                }}
              >
                <Tab key="visual" title="Визуальный" />
                <Tab key="code" title="Код" />
              </Tabs>
            </div>

            <div className="flex-1 min-h-0 overflow-hidden break-words">
              {viewMode === "code" ? (
                <Editor
                  language={
                    currentArtifact.type === "bpmn"
                      ? "xml"
                      : currentArtifact.type === "openapi"
                        ? "json"
                        : "plaintext"
                  }
                  value={currentArtifact.content}
                  options={{
                    readOnly: true,
                    minimap: { enabled: true },
                    wordWrap: "on",
                    fontSize: 12,
                  }}
                  height="100%"
                  theme="vs-dark"
                />
              ) : (
                <div className="h-full">
                  {currentArtifact.type === "bpmn" && (
                    <BpmnVisualization xml={currentArtifact.content} />
                  )}
                  {currentArtifact.type === "openapi" && (
                    <OpenApiVisualization json={currentArtifact.content} />
                  )}
                  {currentArtifact.type === "puml" && (
                    <PumlVisualization content={currentArtifact.content} />
                  )}
                </div>
              )}
            </div>
          </>
        ) : (
          <div className="h-full flex items-center justify-center text-muted">
            Выберите процесс для просмотра
          </div>
        )}
      </div>
    </div>
  );
}