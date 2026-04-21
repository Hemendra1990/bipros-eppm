"use client";

import { useRef, useState } from "react";
import { useParams } from "next/navigation";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import toast from "react-hot-toast";
import { getErrorMessage } from "@/lib/utils/error";
import { documentApi } from "@/lib/api/documentApi";
import { TabTip } from "@/components/common/TabTip";
import type {
  Document,
  DocumentFolder,
  UploadDocumentMetadata,
} from "@/lib/api/documentApi";

interface DocumentFormData {
  title: string;
  documentNumber: string;
  status: "DRAFT" | "UNDER_REVIEW" | "APPROVED" | "SUPERSEDED" | "ARCHIVED";
  description: string;
}

export default function DocumentsPage() {
  const params = useParams();
  const projectId = params.projectId as string;
  const [selectedFolderId, setSelectedFolderId] = useState<string | null>(null);
  const [expandedFolders, setExpandedFolders] = useState<Set<string>>(new Set());
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [formData, setFormData] = useState<DocumentFormData>({
    title: "",
    documentNumber: "",
    status: "DRAFT",
    description: "",
  });
  const [file, setFile] = useState<File | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [error, setError] = useState("");
  const queryClient = useQueryClient();

  // Fetch root folders
  const { data: rootFolders = [] } = useQuery({
    queryKey: ["folders", projectId, "root"],
    queryFn: () => documentApi.listRootFolders(projectId),
    select: (response) => response.data || [],
  });

  // Fetch documents for the project. The backend endpoint returns every
  // document in the project (no folderId query param), so filter client-side
  // by the currently selected folder.
  const { data: documents = [] } = useQuery({
    queryKey: ["documents", projectId, selectedFolderId],
    queryFn: () => documentApi.listDocuments(projectId),
    select: (response) => {
      const all = response.data ?? [];
      return selectedFolderId
        ? all.filter((d) => d.folderId === selectedFolderId)
        : all;
    },
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

  const uploadDocumentMutation = useMutation({
    mutationFn: (vars: { metadata: UploadDocumentMetadata; file: File }) =>
      documentApi.uploadDocument(projectId, vars.metadata, vars.file),
    onSuccess: () => {
      toast.success("Document uploaded successfully");
      setShowCreateForm(false);
      setFormData({
        title: "",
        documentNumber: "",
        status: "DRAFT",
        description: "",
      });
      setFile(null);
      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }
      setError("");
      queryClient.invalidateQueries({ queryKey: ["documents", projectId, selectedFolderId] });
    },
    onError: (err: unknown) => {
      const msg = getErrorMessage(err, "Failed to upload document");
      setError(msg);
      toast.error(msg);
    },
  });

  const handleFormChange = (
    e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>
  ) => {
    const { name, value } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: value as DocumentFormData[keyof DocumentFormData],
    }));
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const picked = e.target.files?.[0] ?? null;
    setFile(picked);
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError("");

    if (!formData.title || !formData.documentNumber) {
      setError("Title and Document Number are required");
      return;
    }
    if (!file) {
      setError("Please pick a file to upload");
      return;
    }

    const metadata: UploadDocumentMetadata = {
      folderId: selectedFolderId,
      documentNumber: formData.documentNumber,
      title: formData.title,
      description: formData.description || null,
      status: formData.status,
    };

    uploadDocumentMutation.mutate({ metadata, file });
  };

  /** Fetches the binary from the API and triggers a browser save dialog. */
  const handleDownload = async (doc: Document) => {
    try {
      const blob = await documentApi.downloadDocument(projectId, doc.id);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = doc.fileName || `${doc.documentNumber || doc.id}.bin`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch (err) {
      toast.error(getErrorMessage(err, "Failed to download document"));
    }
  };

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
                    className="flex items-center justify-center w-5 h-5 hover:bg-slate-700 rounded"
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
                      ? "bg-blue-500/10 text-blue-400 ring-1 ring-blue-500/20"
                      : "hover:bg-slate-800/30 text-slate-300"
                  }`}
                >
                  <span className="text-sm">📁 {folder.name}</span>
                </button>
              </div>
              {hasChildren && isExpanded && (
                <div className="ml-4 border-l border-slate-700/50">
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
    <div className="space-y-6">
      <TabTip
        title="Document Management"
        description="Organize project documents in folders. Track versions, create transmittals for formal document issue to stakeholders."
      />
      <div className="grid grid-cols-4 gap-6 h-full">
        {/* Folder Tree Sidebar */}
      <div className="col-span-1 bg-slate-900/50 rounded-xl border border-slate-800 p-4 overflow-y-auto shadow-xl">
        <h2 className="text-sm font-semibold text-slate-300 mb-4">Folders</h2>
        {rootFolders.length > 0 ? (
          renderFolderTree(rootFolders)
        ) : (
          <p className="text-sm text-slate-500">No folders</p>
        )}
      </div>

      {/* Documents List */}
      <div className="col-span-3 bg-slate-900/50 rounded-xl border border-slate-800 p-6 shadow-xl">
        {selectedFolderId ? (
          <>
            <div className="flex justify-between items-center mb-6">
              <h2 className="text-lg font-semibold text-white">Documents</h2>
              <button
                onClick={() => setShowCreateForm(!showCreateForm)}
                className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-500 transition-colors text-sm font-medium"
              >
                + Upload Document
              </button>
            </div>

            {/* Create Document Form */}
            {showCreateForm && (
              <div className="mb-6 bg-slate-800/50 rounded-lg border border-slate-700 p-4">
                {error && (
                  <div className="mb-3 rounded-md bg-red-500/10 p-3 text-sm text-red-400">
                    {error}
                  </div>
                )}
                <form onSubmit={handleSubmit} className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-sm font-medium text-slate-300 mb-1">
                      Title
                    </label>
                    <input
                      type="text"
                      name="title"
                      value={formData.title}
                      onChange={handleFormChange}
                      placeholder="Document title"
                      className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white placeholder-slate-500 focus:border-blue-500 focus:ring-2 focus:ring-blue-500 focus:ring-opacity-50"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-slate-300 mb-1">
                      Document Number
                    </label>
                    <input
                      type="text"
                      name="documentNumber"
                      value={formData.documentNumber}
                      onChange={handleFormChange}
                      placeholder="e.g., DOC-001"
                      className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white placeholder-slate-500 focus:border-blue-500 focus:ring-2 focus:ring-blue-500 focus:ring-opacity-50"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-slate-300 mb-1">
                      Status
                    </label>
                    <select
                      name="status"
                      value={formData.status}
                      onChange={handleFormChange}
                      className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white focus:border-blue-500 focus:ring-2 focus:ring-blue-500 focus:ring-opacity-50"
                    >
                      <option value="DRAFT">DRAFT</option>
                      <option value="UNDER_REVIEW">UNDER_REVIEW</option>
                      <option value="APPROVED">APPROVED</option>
                      <option value="SUPERSEDED">SUPERSEDED</option>
                      <option value="ARCHIVED">ARCHIVED</option>
                    </select>
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-slate-300 mb-1">
                      File
                    </label>
                    <input
                      ref={fileInputRef}
                      type="file"
                      onChange={handleFileChange}
                      className="block w-full text-sm text-slate-300 file:mr-3 file:py-2 file:px-3 file:rounded-md file:border-0 file:bg-blue-600 file:text-white file:text-sm file:font-medium hover:file:bg-blue-500 bg-slate-800 border border-slate-700 rounded-lg cursor-pointer"
                    />
                    {file && (
                      <p className="mt-1 text-xs text-slate-500">
                        {file.name} — {(file.size / 1024).toFixed(1)} KB
                      </p>
                    )}
                  </div>
                  <div className="col-span-2">
                    <label className="block text-sm font-medium text-slate-300 mb-1">
                      Description (optional)
                    </label>
                    <textarea
                      name="description"
                      value={formData.description}
                      onChange={handleFormChange}
                      placeholder="Brief description of the document"
                      rows={2}
                      className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white placeholder-slate-500 focus:border-blue-500 focus:ring-2 focus:ring-blue-500 focus:ring-opacity-50"
                    />
                  </div>
                  <div className="col-span-2 flex gap-2">
                    <button
                      type="submit"
                      disabled={uploadDocumentMutation.isPending}
                      className="flex-1 px-3 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-500 disabled:bg-slate-600 transition-colors text-sm font-medium"
                    >
                      {uploadDocumentMutation.isPending ? "Uploading..." : "Upload Document"}
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setShowCreateForm(false);
                        setFile(null);
                        if (fileInputRef.current) {
                          fileInputRef.current.value = "";
                        }
                      }}
                      className="flex-1 px-3 py-2 border border-slate-700 bg-slate-800 text-slate-300 rounded-lg hover:bg-slate-700 transition-colors text-sm font-medium"
                    >
                      Cancel
                    </button>
                  </div>
                </form>
              </div>
            )}

            {documents.length > 0 ? (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead className="bg-slate-900/80 border-b border-slate-800">
                    <tr>
                      <th className="px-6 py-3 text-left font-semibold text-slate-400">
                        Title
                      </th>
                      <th className="px-6 py-3 text-left font-semibold text-slate-400">
                        Document #
                      </th>
                      <th className="px-6 py-3 text-left font-semibold text-slate-400">
                        Status
                      </th>
                      <th className="px-6 py-3 text-left font-semibold text-slate-400">
                        Version
                      </th>
                      <th className="px-6 py-3 text-left font-semibold text-slate-400">
                        Updated
                      </th>
                      <th className="px-6 py-3 text-center font-semibold text-slate-400">
                        Actions
                      </th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/50">
                    {documents.map((doc) => (
                      <tr key={doc.id} className="hover:bg-slate-800/30 transition-colors border-slate-800/50">
                        <td className="px-6 py-4 text-white font-medium">{doc.title}</td>
                        <td className="px-6 py-4 text-slate-400">{doc.documentNumber}</td>
                        <td className="px-6 py-4">
                          <span
                            className={`px-3 py-1 rounded-full text-xs font-medium ${
                              doc.status === "APPROVED"
                                ? "bg-emerald-500/10 text-emerald-400 ring-1 ring-emerald-500/20"
                                : doc.status === "DRAFT"
                                  ? "bg-amber-500/10 text-amber-400 ring-1 ring-amber-500/20"
                                  : doc.status === "UNDER_REVIEW"
                                    ? "bg-blue-500/10 text-blue-400 ring-1 ring-blue-500/20"
                                    : doc.status === "SUPERSEDED"
                                      ? "bg-slate-700/50 text-slate-300 ring-1 ring-slate-600/50"
                                      : "bg-red-500/10 text-red-400 ring-1 ring-red-500/20"
                            }`}
                          >
                            {doc.status}
                          </span>
                        </td>
                        <td className="px-6 py-4 text-slate-400">v{doc.currentVersion}</td>
                        <td className="px-6 py-4 text-slate-400 text-xs">
                          {new Date(doc.updatedAt).toLocaleDateString()}
                        </td>
                        <td className="px-6 py-4 text-center">
                          <button
                            onClick={() => handleDownload(doc)}
                            disabled={!doc.filePath}
                            title={doc.filePath ? "Download file" : "No file attached"}
                            className="text-blue-400 hover:text-blue-300 disabled:text-slate-600 disabled:cursor-not-allowed text-xs font-medium"
                          >
                            Download
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <p className="text-slate-500 text-center py-8">No documents in this folder</p>
            )}
          </>
        ) : (
          <div className="text-center py-12">
            <p className="text-slate-500">Select a folder to view documents</p>
          </div>
        )}
      </div>
      </div>
    </div>
  );
}
