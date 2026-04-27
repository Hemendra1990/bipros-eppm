export function findNodeById<T extends { id: string; children?: T[] }>(
  nodes: T[],
  id: string
): T | null {
  for (const node of nodes) {
    if (node.id === id) return node;
    if (node.children) {
      const hit = findNodeById(node.children, id);
      if (hit) return hit;
    }
  }
  return null;
}
