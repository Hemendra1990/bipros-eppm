import { apiClient } from "./client";
import type {
  ApiResponse,
  PagedResponse,
  ProcurementPlanResponse,
  CreateProcurementPlanRequest,
  TenderResponse,
  CreateTenderRequest,
  BidSubmissionResponse,
  CreateBidSubmissionRequest,
  ContractResponse,
  CreateContractRequest,
  ContractMilestoneResponse,
  CreateContractMilestoneRequest,
  VariationOrderResponse,
  CreateVariationOrderRequest,
  PerformanceBondResponse,
  CreatePerformanceBondRequest,
  ContractorScorecardResponse,
  CreateContractorScorecardRequest,
  ContractAttachment,
  UploadContractAttachmentMetadata,
} from "../types";

export const contractApi = {
  // Procurement Plans
  listProcurementPlans: (projectId: string, page = 0, size = 20) =>
    apiClient
      .get<ApiResponse<PagedResponse<ProcurementPlanResponse>>>(
        `/v1/projects/${projectId}/procurement-plans`,
        { params: { page, size } }
      )
      .then((r) => r.data),

  getProcurementPlan: (projectId: string, id: string) =>
    apiClient
      .get<ApiResponse<ProcurementPlanResponse>>(
        `/v1/projects/${projectId}/procurement-plans/${id}`
      )
      .then((r) => r.data),

  createProcurementPlan: (projectId: string, data: CreateProcurementPlanRequest) =>
    apiClient
      .post<ApiResponse<ProcurementPlanResponse>>(
        `/v1/projects/${projectId}/procurement-plans`,
        data
      )
      .then((r) => r.data),

  updateProcurementPlan: (projectId: string, id: string, data: Partial<CreateProcurementPlanRequest>) =>
    apiClient
      .put<ApiResponse<ProcurementPlanResponse>>(
        `/v1/projects/${projectId}/procurement-plans/${id}`,
        data
      )
      .then((r) => r.data),

  deleteProcurementPlan: (projectId: string, id: string) =>
    apiClient.delete(`/v1/projects/${projectId}/procurement-plans/${id}`),

  // Tenders
  listTenders: (projectId: string, page = 0, size = 20) =>
    apiClient
      .get<ApiResponse<PagedResponse<TenderResponse>>>(
        `/v1/projects/${projectId}/tenders`,
        { params: { page, size } }
      )
      .then((r) => r.data),

  getTender: (projectId: string, id: string) =>
    apiClient
      .get<ApiResponse<TenderResponse>>(`/v1/projects/${projectId}/tenders/${id}`)
      .then((r) => r.data),

  createTender: (projectId: string, data: CreateTenderRequest) =>
    apiClient
      .post<ApiResponse<TenderResponse>>(`/v1/projects/${projectId}/tenders`, data)
      .then((r) => r.data),

  updateTender: (projectId: string, id: string, data: Partial<CreateTenderRequest>) =>
    apiClient
      .put<ApiResponse<TenderResponse>>(`/v1/projects/${projectId}/tenders/${id}`, data)
      .then((r) => r.data),

  deleteTender: (projectId: string, id: string) =>
    apiClient.delete(`/v1/projects/${projectId}/tenders/${id}`),

  // Bid Submissions
  listBidSubmissions: (tenderId: string, page = 0, size = 20) =>
    apiClient
      .get<ApiResponse<PagedResponse<BidSubmissionResponse>>>(
        `/v1/tenders/${tenderId}/bids`,
        { params: { page, size } }
      )
      .then((r) => r.data),

  listBidSubmissionsAll: (tenderId: string) =>
    apiClient
      .get<ApiResponse<BidSubmissionResponse[]>>(`/v1/tenders/${tenderId}/bids/all`)
      .then((r) => r.data),

  getBidSubmission: (tenderId: string, id: string) =>
    apiClient
      .get<ApiResponse<BidSubmissionResponse>>(`/v1/tenders/${tenderId}/bids/${id}`)
      .then((r) => r.data),

  createBidSubmission: (tenderId: string, data: CreateBidSubmissionRequest) =>
    apiClient
      .post<ApiResponse<BidSubmissionResponse>>(`/v1/tenders/${tenderId}/bids`, data)
      .then((r) => r.data),

  updateBidSubmission: (tenderId: string, id: string, data: Partial<CreateBidSubmissionRequest>) =>
    apiClient
      .put<ApiResponse<BidSubmissionResponse>>(`/v1/tenders/${tenderId}/bids/${id}`, data)
      .then((r) => r.data),

  deleteBidSubmission: (tenderId: string, id: string) =>
    apiClient.delete(`/v1/tenders/${tenderId}/bids/${id}`),

  // Contracts
  listContracts: (projectId: string, page = 0, size = 20) =>
    apiClient
      .get<ApiResponse<PagedResponse<ContractResponse>>>(
        `/v1/projects/${projectId}/contracts`,
        { params: { page, size } }
      )
      .then((r) => r.data),

  getContract: (projectId: string, id: string) =>
    apiClient
      .get<ApiResponse<ContractResponse>>(`/v1/projects/${projectId}/contracts/${id}`)
      .then((r) => r.data),

  createContract: (projectId: string, data: CreateContractRequest) =>
    apiClient
      .post<ApiResponse<ContractResponse>>(`/v1/projects/${projectId}/contracts`, data)
      .then((r) => r.data),

  updateContract: (projectId: string, id: string, data: Partial<CreateContractRequest>) =>
    apiClient
      .put<ApiResponse<ContractResponse>>(`/v1/projects/${projectId}/contracts/${id}`, data)
      .then((r) => r.data),

  deleteContract: (projectId: string, id: string) =>
    apiClient.delete(`/v1/projects/${projectId}/contracts/${id}`),

  // Contract Milestones
  listContractMilestones: (contractId: string) =>
    apiClient
      .get<ApiResponse<ContractMilestoneResponse[]>>(
        `/v1/contracts/${contractId}/milestones`
      )
      .then((r) => r.data),

  getContractMilestone: (contractId: string, id: string) =>
    apiClient
      .get<ApiResponse<ContractMilestoneResponse>>(
        `/v1/contracts/${contractId}/milestones/${id}`
      )
      .then((r) => r.data),

  createContractMilestone: (contractId: string, data: CreateContractMilestoneRequest) =>
    apiClient
      .post<ApiResponse<ContractMilestoneResponse>>(
        `/v1/contracts/${contractId}/milestones`,
        data
      )
      .then((r) => r.data),

  updateContractMilestone: (contractId: string, id: string, data: Partial<CreateContractMilestoneRequest>) =>
    apiClient
      .put<ApiResponse<ContractMilestoneResponse>>(
        `/v1/contracts/${contractId}/milestones/${id}`,
        data
      )
      .then((r) => r.data),

  deleteContractMilestone: (contractId: string, id: string) =>
    apiClient.delete(`/v1/contracts/${contractId}/milestones/${id}`),

  // Variation Orders
  listVariationOrders: (contractId: string) =>
    apiClient
      .get<ApiResponse<VariationOrderResponse[]>>(
        `/v1/contracts/${contractId}/variation-orders`
      )
      .then((r) => r.data),

  getVariationOrder: (contractId: string, id: string) =>
    apiClient
      .get<ApiResponse<VariationOrderResponse>>(
        `/v1/contracts/${contractId}/variation-orders/${id}`
      )
      .then((r) => r.data),

  createVariationOrder: (contractId: string, data: CreateVariationOrderRequest) =>
    apiClient
      .post<ApiResponse<VariationOrderResponse>>(
        `/v1/contracts/${contractId}/variation-orders`,
        data
      )
      .then((r) => r.data),

  updateVariationOrder: (contractId: string, id: string, data: Partial<CreateVariationOrderRequest>) =>
    apiClient
      .put<ApiResponse<VariationOrderResponse>>(
        `/v1/contracts/${contractId}/variation-orders/${id}`,
        data
      )
      .then((r) => r.data),

  deleteVariationOrder: (contractId: string, id: string) =>
    apiClient.delete(`/v1/contracts/${contractId}/variation-orders/${id}`),

  // Performance Bonds
  listPerformanceBonds: (contractId: string) =>
    apiClient
      .get<ApiResponse<PerformanceBondResponse[]>>(`/v1/contracts/${contractId}/bonds`)
      .then((r) => r.data),

  getPerformanceBond: (contractId: string, id: string) =>
    apiClient
      .get<ApiResponse<PerformanceBondResponse>>(
        `/v1/contracts/${contractId}/bonds/${id}`
      )
      .then((r) => r.data),

  createPerformanceBond: (contractId: string, data: CreatePerformanceBondRequest) =>
    apiClient
      .post<ApiResponse<PerformanceBondResponse>>(
        `/v1/contracts/${contractId}/bonds`,
        data
      )
      .then((r) => r.data),

  updatePerformanceBond: (contractId: string, id: string, data: Partial<CreatePerformanceBondRequest>) =>
    apiClient
      .put<ApiResponse<PerformanceBondResponse>>(
        `/v1/contracts/${contractId}/bonds/${id}`,
        data
      )
      .then((r) => r.data),

  deletePerformanceBond: (contractId: string, id: string) =>
    apiClient.delete(`/v1/contracts/${contractId}/bonds/${id}`),

  // Contractor Scorecards
  listContractorScorecards: (contractId: string) =>
    apiClient
      .get<ApiResponse<ContractorScorecardResponse[]>>(
        `/v1/contracts/${contractId}/scorecards`
      )
      .then((r) => r.data),

  getContractorScorecard: (contractId: string, id: string) =>
    apiClient
      .get<ApiResponse<ContractorScorecardResponse>>(
        `/v1/contracts/${contractId}/scorecards/${id}`
      )
      .then((r) => r.data),

  createContractorScorecard: (contractId: string, data: CreateContractorScorecardRequest) =>
    apiClient
      .post<ApiResponse<ContractorScorecardResponse>>(
        `/v1/contracts/${contractId}/scorecards`,
        data
      )
      .then((r) => r.data),

  updateContractorScorecard: (contractId: string, id: string, data: Partial<CreateContractorScorecardRequest>) =>
    apiClient
      .put<ApiResponse<ContractorScorecardResponse>>(
        `/v1/contracts/${contractId}/scorecards/${id}`,
        data
      )
      .then((r) => r.data),

  deleteContractorScorecard: (contractId: string, id: string) =>
    apiClient.delete(`/v1/contracts/${contractId}/scorecards/${id}`),

  // Contract Attachments — polymorphic, single backing table; the parent
  // resource is encoded in the URL. Upload uses multipart/form-data with
  // a `metadata` JSON part and a `file` binary part.
  listContractAttachments: (projectId: string, contractId: string) =>
    apiClient
      .get<ApiResponse<ContractAttachment[]>>(
        `/v1/projects/${projectId}/contracts/${contractId}/attachments`,
      )
      .then((r) => r.data),

  uploadContractAttachment: (
    projectId: string,
    contractId: string,
    metadata: UploadContractAttachmentMetadata,
    file: File,
  ) => {
    const form = new FormData();
    form.append(
      "metadata",
      new Blob([JSON.stringify(metadata)], { type: "application/json" }),
    );
    form.append("file", file);
    return apiClient
      .post<ApiResponse<ContractAttachment>>(
        `/v1/projects/${projectId}/contracts/${contractId}/attachments`,
        form,
        { headers: { "Content-Type": "multipart/form-data" } },
      )
      .then((r) => r.data);
  },

  listMilestoneAttachments: (
    projectId: string,
    contractId: string,
    milestoneId: string,
  ) =>
    apiClient
      .get<ApiResponse<ContractAttachment[]>>(
        `/v1/projects/${projectId}/contracts/${contractId}/milestones/${milestoneId}/attachments`,
      )
      .then((r) => r.data),

  uploadMilestoneAttachment: (
    projectId: string,
    contractId: string,
    milestoneId: string,
    metadata: UploadContractAttachmentMetadata,
    file: File,
  ) => {
    const form = new FormData();
    form.append(
      "metadata",
      new Blob([JSON.stringify(metadata)], { type: "application/json" }),
    );
    form.append("file", file);
    return apiClient
      .post<ApiResponse<ContractAttachment>>(
        `/v1/projects/${projectId}/contracts/${contractId}/milestones/${milestoneId}/attachments`,
        form,
        { headers: { "Content-Type": "multipart/form-data" } },
      )
      .then((r) => r.data);
  },

  listVariationOrderAttachments: (
    projectId: string,
    contractId: string,
    voId: string,
  ) =>
    apiClient
      .get<ApiResponse<ContractAttachment[]>>(
        `/v1/projects/${projectId}/contracts/${contractId}/variation-orders/${voId}/attachments`,
      )
      .then((r) => r.data),

  uploadVariationOrderAttachment: (
    projectId: string,
    contractId: string,
    voId: string,
    metadata: UploadContractAttachmentMetadata,
    file: File,
  ) => {
    const form = new FormData();
    form.append(
      "metadata",
      new Blob([JSON.stringify(metadata)], { type: "application/json" }),
    );
    form.append("file", file);
    return apiClient
      .post<ApiResponse<ContractAttachment>>(
        `/v1/projects/${projectId}/contracts/${contractId}/variation-orders/${voId}/attachments`,
        form,
        { headers: { "Content-Type": "multipart/form-data" } },
      )
      .then((r) => r.data);
  },

  listPerformanceBondAttachments: (
    projectId: string,
    contractId: string,
    bondId: string,
  ) =>
    apiClient
      .get<ApiResponse<ContractAttachment[]>>(
        `/v1/projects/${projectId}/contracts/${contractId}/bonds/${bondId}/attachments`,
      )
      .then((r) => r.data),

  uploadPerformanceBondAttachment: (
    projectId: string,
    contractId: string,
    bondId: string,
    metadata: UploadContractAttachmentMetadata,
    file: File,
  ) => {
    const form = new FormData();
    form.append(
      "metadata",
      new Blob([JSON.stringify(metadata)], { type: "application/json" }),
    );
    form.append("file", file);
    return apiClient
      .post<ApiResponse<ContractAttachment>>(
        `/v1/projects/${projectId}/contracts/${contractId}/bonds/${bondId}/attachments`,
        form,
        { headers: { "Content-Type": "multipart/form-data" } },
      )
      .then((r) => r.data);
  },

  downloadContractAttachment: (
    projectId: string,
    contractId: string,
    attachmentId: string,
    disposition: "attachment" | "inline" = "attachment",
  ) =>
    apiClient
      .get<Blob>(
        `/v1/projects/${projectId}/contracts/${contractId}/attachments/${attachmentId}/download`,
        { responseType: "blob", params: { disposition } },
      )
      .then((r) => r.data),

  deleteContractAttachment: (
    projectId: string,
    contractId: string,
    attachmentId: string,
  ) =>
    apiClient.delete(
      `/v1/projects/${projectId}/contracts/${contractId}/attachments/${attachmentId}`,
    ),
};
