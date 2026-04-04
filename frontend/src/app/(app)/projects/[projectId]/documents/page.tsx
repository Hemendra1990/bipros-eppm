"use client";

import { useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { documentApi } from "@/lib/api/documentApi";
import type { DocumentFolder, Document } from "@/lib/api/documentApi";

export default function DocumentsPage() {
  const params = useParams();
  const projectId = params.projectId as string;
  const [selectedFolderId, setSelectedFolderId] = useState<string | null>(null);
  const [expandedFolders, setExpandedFolders] = useState<Set<string>>(new Set());
  const queryClient = useQueryClient();

  // Fetch root folders
  const { data: rootFolders = [] } = useQuery({
    queryKey: ["folders", projectId, "root"],
    queryFn: () => documentApi.listRootFolders(projectId),
    select: (response) => response.data || [],
  });

  // Fetch documents in selected folder
  const { data: documents = [] } = useQuery({
    queryKey: ["documents", projectId, selectedFolderId],
    queryFn: () => documentApi.listDocuments(projectId),
    select: (response) => response.data || [],
    enabled: !!selectedFolderId,
  });

  // Fetch child folders
  const { data: childFolders = {} } = useQuery({
    queryKey: ["folders", projectId, "children"],
    queryFn: async () => {
      const result: Record<string, DocumentFolder[]> = {};
      for (const folderId of expandedFolders) {
        const response = await documentApi.listChildFolders(projectId, folderId);
        result[folderId] = response.data || [];
      }
      return result;
    },
  });

  const toggleFolderExpansion = (folderId: string) => {
    const newExpanded = new Set(expandedFolders);
    if (newExpanded.has(folderId)) {
      newExpanded.delete(folderId);
    } else {
      newExpanded.add(folderId);
    }
    setExpandedFolders(newExpanded);
  };

  const getFolderChildren = (folderId: string): DocumentFolder[] => {
    return childFolders[folderId] || [];
  };

  const renderFolderTree = (folders: DocumentFolder[], depth = 0) => {
    return (
      <ul className="space-y-1">
        {folders.map((folder) => {
          const children = getFolderChildren(folder.id);
          const isExpanded = expandedFolders.has(folder.id);
          const hasChildren = children.length > 0;

          return (
            <li key={folder.id}>
              <div className="flex items-center gap-2">
                {hasChildren && (
                  <button
                    onClick={() => toggleFolderExpansion(folder.id)}
                    className="flex items-center justify-center w-5 h-5 hover:bg-gray-200 rounded"
                  >
                    <span className={`transition-transform ${isExpanded ? "rotate-90" : ""}`}>
                      ▶
                    </span>
                  </button>
                )}
                {!hasChildren && <div className="w-5" />}
                <button
                  onClick={() => setSelectedFolderId(folder.id)}
                  className={`flex-1 text-left px-2 py-1 rounded transition-colors ${
                    selectedFolderId === folder.id
                      ? "bg-blue-100 text-blue-700"
                      : "hover:bg-gray-100 text-gray-700"
                  }`}
                >
                  <span className="text-sm">📁 {folder.name}</span>
                </button>
              </div>
              {hasChildren && isExpanded && (
                <div className="ml-4 border-l border-gray-200">
                  {renderFolderTree(children, depth + 1)}
                </div>
              )}
            </li>
          );
        })}
      </ul>
    );
  };

  return (
    <div className="grid grid-cols-4 gap-6 h-full">
      {/* Folder Tree Sidebar */}
      <div className="col-span-1 bg-white rounded-lg border border-gray-200 p-4 overflow-y-auto">
        <h2 className="text-sm font-semibold text-gray-700 mb-4">Folders</h2>
        {rootFolders.length > 0 ? (
          renderFolderTree(rootFolders)
        ) : (
          <p className="text-sm text-gray-500">No folders</p>
        )}
      </div>

      {/* Documents List */}
      <div className="col-span-3 bg-white rounded-lg border border-gray-200 p-6">
        {selectedFolderId ? (
          <>
            <div className="flex justify-between items-center mb-6">
              <h2 className="text-lg font-semibold text-gray-900">Documents</h2>
              <button className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors text-sm font-medium">
                + Upload Document
              </button>
            </div>

            {documents.length > 0 ? (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead className="bg-gray-50 border-b border-gray-200">
                    <tr>
                      <th className="px-6 py-3 text-left font-semibold text-gray-700">
                        Title
                      </th>
                      <th className="px-6 py-3 text-left font-semibold text-gray-700">
                        Document #
                      </th>
                      <th className="px-6 py-3 text-left font-semibold text-gray-700">
                        Status
                      </th>
                      <th className="px-6 py-3 text-left font-semibold text-gray-700">
                        Version
                      </th>
                      <th className="px-6 py-3 text-left font-semibold text-gray-700">
                        Updated
                      </th>
                      <th className="px-6 py-3 text-center font-semibold text-gray-700">
                        Actions
                      </th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-200">
                    {documents.map((doc) => (
                      <tr key={doc.id} className="hover:bg-gray-50 transition-colors">
                        <td className="px-6 py-4 text-gray-900 font-medium">{doc.title}</td>
                        <td className="px-6 py-4 text-gray-600">{doc.documentNumber}</td>
                        <td className="px-6 py-4">
                          <span
                            className={`px-3 py-1 rounded-full text-xs font-medium ${
                              doc.status === "APPROVED"
                                ? "bg-green-100 text-green-800"
                                : doc.status === "DRAFT"
                                  ? "bg-yellow-100 text-yellow-800"
                                  : doc.status === "UNDER_REVIEW"
                                    ? "bg-blue-100 text-blue-800"
                                    : doc.status === "SUPERSEDED"
                                      ? "bg-gray-100 text-gray-800"
                                      : "bg-red-100 text-red-800"
                            }`}
                          >
                            {doc.status}
                          </span>
                        </td>
                        <td className="px-6 py-4 text-gray-600">v{doc.currentVersion}</td>
                        <td className="px-6 py-4 text-gray-600 text-xs">
                          {new Date(doc.updatedAt).toLocaleDateString()}
                        </td>
                        <td className="px-6 py-4 text-center">
                          <button className="text-blue-600 hover:text-blue-800 text-xs font-medium">
                            View
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <p className="text-gray-500 text-center py-8">No documents in this folder</p>
            )}
          </>
        ) : (
          <div className="text-center py-12">
            <p className="text-gray-500">Select a folder to view documents</p>
          </div>
        )}
      </div>
    </div>
  );
}
