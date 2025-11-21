import { useEffect, useRef, useState } from "react";
import { Arrow, Group, Layer, Rect, Stage, Text } from "react-konva";

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

interface TestFlowCanvasProps {
  selectedNodeId?: string | null;
  onSelect?: (nodeId: string, node: ProcessNode) => void;
  linkSourceId?: string | null;
}

export const TestFlowCanvas = ({
  selectedNodeId,
  onSelect,
  linkSourceId,
}: TestFlowCanvasProps) => {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [dimensions, setDimensions] = useState({ width: 800, height: 600 });
  const [edgeColor, setEdgeColor] = useState("#00a6ff");
  const nodes = useTestFlowStore((state) => state.processNodes);
  const edges = useTestFlowStore((state) => state.processEdges);
  const updateProcessNodePosition = useTestFlowStore(
    (state) => state.updateProcessNodePosition,
  );

  useEffect(() => {
    const resize = () => {
      if (!containerRef.current) return;
      setDimensions({
        width: containerRef.current.clientWidth,
        height: containerRef.current.clientHeight,
      });
    };

    resize();
    window.addEventListener("resize", resize);

    return () => window.removeEventListener("resize", resize);
  }, []);

  useEffect(() => {
    const updateColor = () => {
      const value = getComputedStyle(document.documentElement)
        .getPropertyValue("--app-primary")
        .trim();
      if (value) setEdgeColor(value);
    };

    updateColor();
    const observer = new MutationObserver(updateColor);
    observer.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ["data-theme"],
    });

    return () => observer.disconnect();
  }, []);

  const getPoints = (from: ProcessNode, to: ProcessNode) => {
    const fw = 90;
    const fh = 35;
    const tx = to.position.x;
    const ty = to.position.y;
    const fx = from.position.x;
    const fy = from.position.y;
    const dx = tx - fx;
    const dy = ty - fy;
    let sx = fx;
    let sy = fy;
    let ex = tx;
    let ey = ty;
    if (Math.abs(dx) > Math.abs(dy)) {
      sx = dx > 0 ? fx + fw : fx - fw;
      sy = fy;
      ex = dx > 0 ? tx - fw : tx + fw;
      ey = ty;
    } else {
      sx = fy < ty ? fx : fx;
      sy = dy > 0 ? fy + fh : fy - fh;
      ex = tx;
      ey = dy > 0 ? ty - fh : ty + fh;
    }
    return [sx, sy, ex, ey];
  };

  return (
    <div
      ref={containerRef}
      className="h-[600px] w-full rounded-2xl border border-white/10 canvas-grid-bg overflow-hidden"
    >
      <Stage height={dimensions.height} width={dimensions.width} pixelRatio={1}>
        <Layer listening={false}>
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
          {nodes.map((node) => (
            <Group
              key={node.id}
              draggable
              x={node.position.x}
              y={node.position.y}
              onClick={() => onSelect?.(node.id, node)}
              onDragEnd={(event) => {
                updateProcessNodePosition(node.id, {
                  x: event.target.x(),
                  y: event.target.y(),
                });
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
              />
              <Text
                align="center"
                fill="#334155"
                fontSize={11}
                offset={{ x: 85, y: -5 }}
                text={`${node.metadata?.method ?? ""} ${node.metadata?.endpoint ?? ""}`}
                width={170}
                wrap="word"
              />
            </Group>
          ))}
        </Layer>
      </Stage>
    </div>
  );
};

