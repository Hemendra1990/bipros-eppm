"use client";

import { useQuery } from "@tanstack/react-query";
import { labourMasterApi } from "@/lib/api/labourMasterApi";
import { GradeReferenceTable } from "@/components/labour-master";

const REGULATORY_NOTES = [
  "All expatriate workers must hold a valid Oman Residence Card (ROP) and work permit issued under the sponsoring contractor.",
  "Heavy equipment operators require a valid Oman driving licence or an internationally recognised equivalent endorsed by the Road Transport Authority (RTA).",
  "All site workers must complete the mandatory site induction and obtain a Site Safety Card before commencing work.",
  "HSE Officers must hold NEBOSH IGC or equivalent; IOSH Managing Safely is the minimum acceptable for supervisory roles.",
  "Crane operators require a valid third-party inspection certificate for the crane and must carry an OPITO or equivalent rigging card.",
  "All workers involved in bitumen/asphalt operations must have completed chemical handling training and carry the relevant certification.",
  "Traffic Management Officers must produce a current Traffic Management Certificate aligned with PDO/Ministry of Transport requirements.",
  "Daily rates are inclusive of basic salary, accommodation, transport, and food allowance as per standard GCC construction norms.",
  "Omanisation targets must be met per MOM guidelines; Security and Environmental Officer roles have preference for Omani nationals.",
  "All certifications must be verified and copies retained in the HR dossier prior to site mobilisation.",
];

export default function ReferencePage() {
  const grades = useQuery({
    queryKey: ["labour-grades"],
    queryFn: () => labourMasterApi.designations.listGrades(),
  });
  if (grades.isLoading) return <p>Loading…</p>;
  return <GradeReferenceTable rows={grades.data?.data ?? []} regulatoryNotes={REGULATORY_NOTES} />;
}
