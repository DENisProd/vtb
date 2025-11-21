import { useEffect, useMemo, useRef, useState } from "react";
import { Arrow, Group, Layer, Rect, Stage, Text } from "react-konva";
import {
  Button,
  Card,
  CardBody,
  CardHeader,
  Chip,
  Slider,
} from "@heroui/react";
import {
  ArrowPathIcon,
  BackwardIcon,
  ForwardIcon,
  PlayIcon,
} from "@heroicons/react/24/outline";
import type { ProcessNode, StepExecutionStatus } from "@/types/testflow";
import { useTestFlowStore } from "@/stores/testflow-store";

const statusColors: Record<StepExecutionStatus, string> = {
  pending: "#CBD5F5",
  running: "#F59E0B",
  success: "#22C55E",
  warning: "#FB923C",
  failed: "#EF4444",
  skipped: "#94A3B8",
};

export const ProjectChainCanvas = ({ onSelectNode }: { onSelectNode?: (nodeId: string) => void }) => {
  const nodes = useTestFlowStore((state) => state.processNodes);
  const edges = useTestFlowStore((state) => state.processEdges);
  const mappingResult = useTestFlowStore((state) => state.mappingResult);
  const executions = useTestFlowStore((state) => state.runnerExecutions);
  const buildGraphFromMapping = useTestFlowStore((state) => state.buildGraphFromMapping);
  const selectedScenarioId = useTestFlowStore((state) => state.selectedScenarioId);
  const connectScenarioSteps = useTestFlowStore((state) => state.connectScenarioSteps);
  const updateProcessNodePosition = useTestFlowStore(
    (state) => state.updateProcessNodePosition,
  );
  
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [playbackIndex, setPlaybackIndex] = useState(0);
  const [linkMode, setLinkMode] = useState(false);
  const [linkSourceId, setLinkSourceId] = useState<string | null>(null);
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [dimensions, setDimensions] = useState({ width: 800, height: 600 });
  const [edgeColor, setEdgeColor] = useState("#00a6ff");
  const [layoutPositions, setLayoutPositions] = useState<Record<string, { x: number; y: number }>>({});

  const timeline = useMemo(() => {
    const execution = executions[0];

    if (!execution) return [];

    return execution.steps.map((step, index) => ({
      ...step,
      index,
    }));
  }, [executions]);

  const currentStep = timeline[playbackIndex];

  useEffect(() => {
    // Всегда пытаемся построить граф из mappingResult при монтировании или изменении mappingResult
    if (mappingResult) {
      buildGraphFromMapping();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mappingResult]);

  useEffect(() => {
    const resize = () => {
      if (!containerRef.current) return;
      setDimensions({
        width: containerRef.current.clientWidth,
        height: containerRef.current.clientHeight,
      });
    };

    resize();
    // window.addEventListener("resize", resize);

    // return () => window.removeEventListener("resize", resize);
  }, []);

  useEffect(() => {
    const value = getComputedStyle(document.documentElement)
      .getPropertyValue("--app-primary")
      .trim();
    if (value) setEdgeColor(value);
  }, []);

  useEffect(() => {
    // упрощаем: используем позиции из стора, без авторазмещения
  }, [nodes.length]);

  const getPoints = (from: ProcessNode, to: ProcessNode) => {
    const fw = 180;
    const fh = 70;
  const fpos = from.position;
  const tpos = to.position;
    const fx = fpos.x;
    const fy = fpos.y;
    const tx = tpos.x;
    const ty = tpos.y;
    const dx = tx - fx;
    const dy = ty - fy;

    // Учитываем offset узлов (Rect имеет offset {x: 90, y: 35})
    // Центр узла в позиции (fx, fy), края:
    // - Правый: fx + 90, Левый: fx - 90
    // - Нижний: fy + 35, Верхний: fy - 35
    const halfWidth = fw / 2; // 90
    const halfHeight = fh / 2; // 35

    // Вычисляем точки выхода из узла-источника (от края блока)
    let sx: number;
    let sy: number;
    
    // Вычисляем точки входа в узел-приемник (к краю блока)
    let ex: number;
    let ey: number;

    // Определяем, какая сторона узла-источника ближе к узлу-приемнику
    const absDx = Math.abs(dx);
    const absDy = Math.abs(dy);

    if (absDx > absDy) {
      // Горизонтальное соединение - стрелка идет слева направо или справа налево
      if (dx > 0) {
        // Узел-приемник справа
        sx = fx + halfWidth; // Выход справа от узла-источника
        sy = fy;
        ex = tx - halfWidth; // Вход слева в узел-приемник
        ey = ty;
      } else {
        // Узел-приемник слева
        sx = fx - halfWidth; // Выход слева от узла-источника
        sy = fy;
        ex = tx + halfWidth; // Вход справа в узел-приемник
        ey = ty;
      }
    } else {
      // Вертикальное соединение - стрелка идет сверху вниз или снизу вверх
      if (dy > 0) {
        // Узел-приемник снизу
        sx = fx;
        sy = fy + halfHeight; // Выход снизу от узла-источника
        ex = tx;
        ey = ty - halfHeight; // Вход сверху в узел-приемник
      } else {
        // Узел-приемник сверху
        sx = fx;
        sy = fy - halfHeight; // Выход сверху от узла-источника
        ex = tx;
        ey = ty + halfHeight; // Вход снизу в узел-приемник
      }
    }

    return [sx, sy, ex, ey];
  };

  return (
    <div className="space-y-4">
      <Card className="border border-white/10">
        <CardHeader className="flex-wrap gap-3">
          <div>
            <div className="text-sm font-semibold text-white">
              Канва процесса
            </div>
            <div className="text-xs text-slate-400">
              react-konva визуализация BPMN/API цепочки ({nodes.length} узлов)
            </div>
          </div>
          <div className="ml-auto flex gap-2">
            <Button
              startContent={<ArrowPathIcon className="h-4 w-4" />}
              variant="flat"
              onPress={() => buildGraphFromMapping()}
            >
              Подгрузить /map
            </Button>
            <Button startContent={<PlayIcon className="h-4 w-4" />}>
              Проиграть
            </Button>
            <Button
              className={linkMode ? "btn-primary" : "btn-ghost"}
              onPress={() => setLinkMode((v) => !v)}
            >
              Связывание
            </Button>
          </div>
        </CardHeader>
        <CardBody>
          <div
            ref={containerRef}
            className="h-[600px] w-full rounded-2xl border border-white/10 canvas-grid-bg relative overflow-hidden"
          >
            <Stage height={dimensions.height} width={dimensions.width} pixelRatio={1} preventDefault={false}>
              <Layer listening={false}>
                {/* Рисуем стрелки */}
                {edges.map((edge) => {
                  const from = nodes.find((node) => node.id === edge.from);
                  const to = nodes.find((node) => node.id === edge.to);
                  if (!from || !to) return null;
                  const [sx, sy, ex, ey] = getPoints(from, to);
                  return (
                    <Arrow
                      key={edge.id}
                      points={[sx, sy, ex, ey]}
                      stroke={edgeColor}
                      fill={edgeColor}
                      strokeWidth={2}
                      opacity={0.7}
                      pointerLength={10}
                      pointerWidth={10}
                      listening={false}
                    />
                  );
                })}
              </Layer>
              <Layer>
                {/* Рисуем узлы */}
                {nodes.map((node) => {
                  const pos = node.position;
                  const endpointText = node.metadata?.method && node.metadata?.endpoint
                    ? `${String(node.metadata.method).toUpperCase()} ${String(node.metadata.endpoint)}`
                    : "";

                  return (
                    <Group
                      key={node.id}
                      draggable
                      x={pos.x}
                      y={pos.y}
                      onClick={() => {
                        if (linkMode) {
                          if (!linkSourceId) {
                            setLinkSourceId(node.id);
                          } else if (linkSourceId !== node.id && selectedScenarioId) {
                            connectScenarioSteps(selectedScenarioId, linkSourceId, node.id);
                            setLinkSourceId(null);
                          } else {
                            setLinkSourceId(null);
                          }
                        }
                        setSelectedNodeId(node.id);
                        onSelectNode?.(node.id);
                      }}
                      onDragStart={() => {
                        setSelectedNodeId(node.id);
                      }}
                      onDragEnd={(event) => {
                        const newPos = { x: event.target.x(), y: event.target.y() };
                        updateProcessNodePosition(node.id, newPos);
                        setLayoutPositions((prev) => ({ ...prev, [node.id]: newPos }));
                      }}
                    >
                      <Rect
                        cornerRadius={16}
                        fill={statusColors[node.status]}
                        height={70}
                        offset={{ x: 90, y: 35 }}
                        shadowBlur={selectedNodeId === node.id || linkSourceId === node.id ? 16 : 6}
                        shadowColor="#0f172a"
                        shadowOpacity={selectedNodeId === node.id || linkSourceId === node.id ? 0.35 : 0.2}
                        stroke={linkSourceId === node.id ? "#F59E0B" : selectedNodeId === node.id ? "#2563EB" : "#ffffff"}
                        strokeWidth={linkSourceId === node.id || selectedNodeId === node.id ? 3 : 1}
                        width={180}
                      />
                      <Text
                        align="center"
                        fill="#0f172a"
                        fontSize={14}
                        offset={{ x: 85, y: 25 }}
                        text={node.label}
                        width={170}
                        wrap="word"
                        listening={false}
                      />
                      {endpointText && (
                        <Text
                          align="center"
                          fill="#334155"
                          fontSize={11}
                          offset={{ x: 85, y: -5 }}
                          text={endpointText}
                          width={170}
                          wrap="word"
                          listening={false}
                        />
                      )}
                    </Group>
                  );
                })}
              </Layer>
            </Stage>
          </div>
        </CardBody>
      </Card>

      <Card className="border border-white/10">
        <CardHeader className="flex-col items-start gap-2">
          <div className="text-sm font-semibold text-white">
            Проигрывание и логи
          </div>
          <div className="flex w-full items-center gap-2">
            <Button
              size="sm"
              startContent={<BackwardIcon className="h-4 w-4" />}
              variant="light"
              onPress={() =>
                setPlaybackIndex((index) => Math.max(index - 1, 0))
              }
            />
            <Slider
              aria-label="Проигрывание"
              className="flex-1"
              maxValue={Math.max(timeline.length - 1, 0)}
              minValue={0}
              value={playbackIndex}
              onChange={(value) =>
                setPlaybackIndex(
                  Array.isArray(value) ? Number(value[0]) : Number(value),
                )
              }
            />
            <Button
              size="sm"
              startContent={<ForwardIcon className="h-4 w-4" />}
              variant="light"
              onPress={() =>
                setPlaybackIndex((index) =>
                  Math.min(index + 1, Math.max(timeline.length - 1, 0)),
                )
              }
            />
          </div>
        </CardHeader>
        <CardBody className="grid grid-cols-1 gap-3 md:grid-cols-2">
          <div className="rounded-2xl border border-white/10 p-4">
            <div className="text-xs uppercase text-slate-500">Текущий шаг</div>
            {currentStep ? (
              <>
                <div className="mt-1 text-lg font-semibold text-white">
                  {currentStep.stepId}
                </div>
                <Chip className="mt-2" size="sm">
                  {currentStep.status}
                </Chip>
                <div className="mt-2 text-xs text-slate-400">
                  Попытка {currentStep.attempt ?? 1}
                </div>
                <div className="text-xs text-slate-400">
                  Длительность {currentStep.durationMs ?? 0}ms
                </div>
              </>
            ) : (
              <div className="mt-2 text-sm text-slate-500">
                Нет данных о прогоне.
              </div>
            )}
          </div>
          <div className="rounded-2xl border border-white/10 p-4">
            <div className="text-xs uppercase text-slate-500">Логи шага</div>
            <div className="mt-2 max-h-[160px] overflow-y-auto text-xs text-slate-300">
              {currentStep
                ? executions[0]?.logs
                    .filter((log) => log.stepId === currentStep.stepId)
                    .map((log) => (
                      <div
                        key={log.id}
                        className="mb-2 rounded-lg bg-white/5 p-2 text-white"
                      >
                        <div className="text-[10px] text-slate-400">
                          {log.timestamp}
                        </div>
                        <div>{log.message}</div>
                      </div>
                    ))
                : "Выберите шаг"}
            </div>
          </div>
        </CardBody>
      </Card>
    </div>
  );
};

