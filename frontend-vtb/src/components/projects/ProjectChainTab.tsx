import { useState, useMemo, useEffect } from "react";
import { Card, CardBody, CardHeader, Button, Input, Select, SelectItem } from "@heroui/react";
import {
  PlusIcon,
  LinkIcon,
  TrashIcon,
} from "@heroicons/react/24/outline";
import { ProjectDto } from "@/lib/testflow-api";
import { useTestFlowStore } from "@/stores/testflow-store";
import { ProjectChainCanvas } from "./ProjectChainCanvas";

interface ChainNode {
  id: string;
  label: string;
  endpoint?: string;
  method?: string;
  position: { x: number; y: number };
  width: number;
  height: number;
}

interface ChainEdge {
  id: string;
  from: string;
  to: string;
}

export function ProjectChainTab({ project }: { project: ProjectDto }) {
  const mappingResult = project.mappingResult;
  const storeNodes = useTestFlowStore((state) => state.processNodes);
  const [nodes, setNodes] = useState<ChainNode[]>([]);
  const [edges, setEdges] = useState<ChainEdge[]>([]);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [linkMode, setLinkMode] = useState(false);
  const [linkSourceId, setLinkSourceId] = useState<string | null>(null);
  const [newNodeData, setNewNodeData] = useState({
    label: "",
    endpoint: "",
    method: "GET",
  });

  // Инициализация узлов из mappingResult
  useEffect(() => {
    if (mappingResult && mappingResult.taskMappings) {
      // Прокидываем mappingResult в глобальный store для канвы
      useTestFlowStore.setState({ mappingResult });
      try {
        useTestFlowStore.getState().buildGraphFromMapping();
      } catch (_) {
        // no-op
      }

      const initialNodes: ChainNode[] = Object.entries(mappingResult.taskMappings).map(
        ([taskId, mapping], idx) => {
          const col = idx % 4;
          const row = Math.floor(idx / 4);
          return {
            id: taskId,
            label: mapping.taskName,
            endpoint: mapping.endpointPath,
            method: mapping.endpointMethod,
            position: { x: col * 250 + 100, y: row * 150 + 100 },
            width: 220,
            height: 80,
          };
        },
      );
      setNodes(initialNodes);

      // Инициализация связей из dataFlowEdges
      const initialEdges: ChainEdge[] = (mappingResult.dataFlowEdges || []).map((edge, idx) => ({
        id: `edge-${idx}`,
        from: edge.sourceTaskId,
        to: edge.targetTaskId,
      }));
      setEdges(initialEdges);
    }
  }, [mappingResult]);

  const selectedNode = useMemo(
    () => storeNodes.find((n) => n.id === selectedNodeId) || null,
    [storeNodes, selectedNodeId],
  );

  const handleCreateNode = () => {
    if (!newNodeData.label) return;

    const newId = `node-${Date.now()}`;
    const newNodes = [...nodes];
    const col = newNodes.length % 4;
    const row = Math.floor(newNodes.length / 4);
    const newNode: ChainNode = {
      id: newId,
      label: newNodeData.label,
      endpoint: newNodeData.endpoint,
      method: newNodeData.method,
      position: { x: col * 250 + 100, y: row * 150 + 100 },
      width: 220,
      height: 80,
    };

    setNodes([...nodes, newNode]);
    setNewNodeData({ label: "", endpoint: "", method: "GET" });
    setSelectedNodeId(newId);
  };

  const handleDeleteNode = (nodeId: string) => {
    setNodes(nodes.filter((n) => n.id !== nodeId));
    setEdges(edges.filter((e) => e.from !== nodeId && e.to !== nodeId));
    if (selectedNodeId === nodeId) setSelectedNodeId(null);
  };

  const handleNodeMove = (nodeId: string, position: { x: number; y: number }) => {
    setNodes(nodes.map((n) => (n.id === nodeId ? { ...n, position } : n)));
  };

  const handleNodeSelect = (nodeId: string) => {
    if (linkMode) {
      if (!linkSourceId) {
        setLinkSourceId(nodeId);
      } else if (linkSourceId !== nodeId) {
        // Создаем связь
        const newEdge: ChainEdge = {
          id: `edge-${Date.now()}`,
          from: linkSourceId,
          to: nodeId,
        };
        setEdges([...edges, newEdge]);
        setLinkSourceId(null);
        setLinkMode(false);
      } else {
        setLinkSourceId(null);
      }
    } else {
      setSelectedNodeId(nodeId);
    }
  };

  const handleUpdateNode = (updates: Partial<ChainNode>) => {
    if (!selectedNodeId) return;
    useTestFlowStore.setState((state) => ({
      processNodes: state.processNodes.map((n) =>
        n.id === selectedNodeId
          ? {
              ...n,
              label: updates.label ?? n.label,
              metadata: {
                ...n.metadata,
                endpoint: updates.endpoint ?? n.metadata?.endpoint,
                method: updates.method ?? n.metadata?.method,
              },
            }
          : n,
      ),
    }));
  };

  const handleDeleteEdge = (edgeId: string) => {
    setEdges(edges.filter((e) => e.id !== edgeId));
  };

  return (
    <div className="flex gap-4 h-full min-h-[600px]">
      {/* Левая панель - инструменты */}
      <div className="w-72 shrink-0 app-card p-4 flex flex-col min-h-0 space-y-4">
        <div>
          <div className="text-sm font-semibold text-[var(--app-text)] mb-3">
            Панель инструментов
          </div>

          {/* Создание нового блока */}
          <Card className="app-card mb-4">
            <CardHeader>
              <div className="text-xs font-semibold text-[var(--app-text)]">
                Создать блок
              </div>
            </CardHeader>
            <CardBody className="space-y-2">
              <Input
                size="sm"
                label="Название"
                placeholder="Название блока"
                value={newNodeData.label}
                onChange={(e) =>
                  setNewNodeData({ ...newNodeData, label: e.target.value })
                }
              />
              <Input
                size="sm"
                label="Endpoint"
                placeholder="/api/endpoint"
                value={newNodeData.endpoint}
                onChange={(e) =>
                  setNewNodeData({ ...newNodeData, endpoint: e.target.value })
                }
              />
              <Select
                size="sm"
                label="Метод"
                selectedKeys={[newNodeData.method]}
                onSelectionChange={(keys) => {
                  const method = Array.from(keys)[0] as string;
                  setNewNodeData({ ...newNodeData, method });
                }}
              >
                <SelectItem key="GET">GET</SelectItem>
                <SelectItem key="POST">POST</SelectItem>
                <SelectItem key="PUT">PUT</SelectItem>
                <SelectItem key="PATCH">PATCH</SelectItem>
                <SelectItem key="DELETE">DELETE</SelectItem>
              </Select>
              <Button
                size="sm"
                className="btn-primary w-full"
                startContent={<PlusIcon className="w-4 h-4" />}
                onPress={handleCreateNode}
                isDisabled={!newNodeData.label}
              >
                Создать блок
              </Button>
            </CardBody>
          </Card>

          {/* Управление связями */}
          <div className="space-y-2">
            <Button
              size="sm"
              variant={linkMode ? "solid" : "bordered"}
              className={linkMode ? "btn-primary" : ""}
              startContent={<LinkIcon className="w-4 h-4" />}
              onPress={() => {
                setLinkMode(!linkMode);
                setLinkSourceId(null);
              }}
            >
              {linkMode ? "Отменить связывание" : "Связать блоки"}
            </Button>
            {linkMode && (
              <div className="text-xs text-muted">
                Выберите первый блок, затем второй для создания связи
              </div>
            )}
          </div>

          {/* Список блоков */}
          <div className="flex-1 min-h-0 flex flex-col">
            <div className="text-xs font-semibold text-[var(--app-text)] mb-2 shrink-0">
              Блоки ({nodes.length})
            </div>
            <div className="space-y-1 flex-1 min-h-0 overflow-y-auto">
              {nodes.map((node) => (
                <div
                  key={node.id}
                  className={`p-2 rounded text-xs cursor-pointer transition-colors ${
                    selectedNodeId === node.id
                      ? "bg-[var(--app-primary)]/20 border border-[var(--app-primary)]/50"
                      : "hover:bg-white/5"
                  }`}
                  onClick={() => setSelectedNodeId(node.id)}
                >
                  <div className="font-medium text-[var(--app-text)]">{node.label}</div>
                  {node.method && node.endpoint && (
                    <div className="text-muted text-[10px]">
                      {node.method} {node.endpoint}
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>

          {/* Список связей */}
          {edges.length > 0 && (
            <div>
              <div className="text-xs font-semibold text-[var(--app-text)] mb-2">
                Связи ({edges.length})
              </div>
              <div className="space-y-1 max-h-32 overflow-y-auto">
                {edges.map((edge) => {
                  const from = nodes.find((n) => n.id === edge.from);
                  const to = nodes.find((n) => n.id === edge.to);
                  return (
                    <div
                      key={edge.id}
                      className="flex items-center justify-between p-2 rounded text-xs bg-white/5"
                    >
                      <div className="text-muted">
                        {from?.label || edge.from} → {to?.label || edge.to}
                      </div>
                      <Button
                        isIconOnly
                        size="sm"
                        variant="light"
                        onPress={() => handleDeleteEdge(edge.id)}
                      >
                        <TrashIcon className="w-3 h-3" />
                      </Button>
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Центральная зона - канва */}
      <div className="flex-1 app-card p-4 flex flex-col">
        <div className="mb-4 flex items-center justify-between">
          <div className="text-sm font-semibold text-[var(--app-text)]">
            Цепочка вызовов
          </div>
          {selectedNodeId && (
            <Button
              size="sm"
              variant="light"
              color="danger"
              startContent={<TrashIcon className="w-4 h-4" />}
              onPress={() => handleDeleteNode(selectedNodeId)}
            >
              Удалить блок
            </Button>
          )}
        </div>
        <div className="flex-1 min-h-0">
          <ProjectChainCanvas onSelectNode={(id) => setSelectedNodeId(id)} />
        </div>
      </div>

      {/* Правая панель - свойства выбранного блока */}
      <div className="w-72 shrink-0 app-card p-4 flex flex-col min-h-0">
        <div className="text-sm font-semibold text-[var(--app-text)] mb-4">
          Свойства блока
        </div>
        {selectedNode ? (
          <div className="space-y-4">
            <Input
              size="sm"
              label="Название"
              value={selectedNode.label}
              onChange={(e) => handleUpdateNode({ label: e.target.value })}
            />
            <Input
              size="sm"
              label="Endpoint"
              value={String((selectedNode.metadata as any)?.endpoint ?? "")}
              onChange={(e) => handleUpdateNode({ endpoint: e.target.value })}
            />
            <Select
              size="sm"
              label="Метод"
              selectedKeys={[String((selectedNode.metadata as any)?.method || "GET")]}
              onSelectionChange={(keys) => {
                const method = Array.from(keys)[0] as string;
                handleUpdateNode({ method });
              }}
            >
              <SelectItem key="GET">GET</SelectItem>
              <SelectItem key="POST">POST</SelectItem>
              <SelectItem key="PUT">PUT</SelectItem>
              <SelectItem key="PATCH">PATCH</SelectItem>
              <SelectItem key="DELETE">DELETE</SelectItem>
            </Select>
            <div className="text-xs text-muted">
              Позиция: ({Math.round(selectedNode.position.x)},{" "}
              {Math.round(selectedNode.position.y)})
            </div>
          </div>
        ) : (
          <div className="text-xs text-muted">
            Выберите блок для просмотра и редактирования свойств
          </div>
        )}
      </div>
    </div>
  );
}
