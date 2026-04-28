"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import toast from "react-hot-toast";
import {
  Plus,
  Trash2,
  CheckCircle2,
  AlertTriangle,
  Star,
  StarOff,
  Plug,
} from "lucide-react";

import { llmProvidersApi } from "@/lib/api/llmProvidersApi";
import type { LlmProviderKind, LlmProviderResponse } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Input, Field, Label, FieldError } from "@/components/ui/input";
import {
  Card,
  CardHeader,
  CardTitle,
  CardDescription,
  CardContent,
} from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogBody,
  DialogFooter,
} from "@/components/ui/dialog";
import {
  Table,
  TableHeader,
  TableBody,
  TableRow,
  TableHead,
  TableCell,
} from "@/components/ui/table";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import UsageTab from "@/components/settings/UsageTab";

type ProviderMeta = {
  value: LlmProviderKind;
  label: string;
  defaultModel: string;
  needsKey: boolean;
};

const PROVIDERS: ProviderMeta[] = [
  { value: "ANTHROPIC",    label: "Anthropic Claude",     defaultModel: "claude-sonnet-4-6",      needsKey: true  },
  { value: "OPENAI",       label: "OpenAI GPT",           defaultModel: "gpt-4o-2024-08-06",      needsKey: true  },
  { value: "GOOGLE",       label: "Google Gemini",        defaultModel: "gemini-1.5-pro",         needsKey: true  },
  { value: "OLLAMA",       label: "Ollama (self-hosted)", defaultModel: "llama3",                 needsKey: false },
  { value: "AZURE_OPENAI", label: "Azure OpenAI",         defaultModel: "gpt-4o",                 needsKey: true  },
  { value: "MISTRAL",      label: "Mistral",              defaultModel: "mistral-large-latest",   needsKey: true  },
];

const formSchema = z.object({
  provider: z.enum(["ANTHROPIC", "OPENAI", "GOOGLE", "OLLAMA", "AZURE_OPENAI", "MISTRAL"]),
  modelName: z.string().min(1).max(128),
  displayName: z.string().min(1).max(128),
  apiKey: z.string().optional(),
  endpointOverride: z.string().max(512).optional().or(z.literal("")),
  isDefault: z.boolean(),
});
type FormValues = z.infer<typeof formSchema>;

export default function LlmProvidersPage() {
  const qc = useQueryClient();
  const [addOpen, setAddOpen] = useState(false);

  const { data: providers, isLoading } = useQuery({
    queryKey: ["llm-providers"],
    queryFn: async () => (await llmProvidersApi.list()).data ?? [],
  });

  const invalidate = () => qc.invalidateQueries({ queryKey: ["llm-providers"] });

  const create = useMutation({
    mutationFn: llmProvidersApi.create,
    onSuccess: () => {
      toast.success("Provider added");
      setAddOpen(false);
      invalidate();
    },
    onError: (e: any) =>
      toast.error(e?.response?.data?.error?.message ?? "Failed to add provider"),
  });

  const setDefault = useMutation({
    mutationFn: llmProvidersApi.setDefault,
    onSuccess: () => {
      toast.success("Default updated");
      invalidate();
    },
    onError: () => toast.error("Failed to set default"),
  });

  const test = useMutation({
    mutationFn: llmProvidersApi.test,
    onSuccess: (r) => {
      toast.success(r.data?.message ?? "Connection ok");
      invalidate();
    },
    onError: (e: any) =>
      toast.error(e?.response?.data?.error?.message ?? "Test failed"),
  });

  const remove = useMutation({
    mutationFn: llmProvidersApi.remove,
    onSuccess: () => {
      toast.success("Provider removed");
      invalidate();
    },
    onError: () => toast.error("Failed to remove provider"),
  });

  return (
    <div className="p-6 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="font-display text-2xl font-semibold text-charcoal">
            LLM Providers
          </h1>
          <p className="text-sm text-slate mt-1 max-w-2xl">
            Configure your own LLM provider keys. The Analytics Assistant uses the
            provider you mark as default. Keys are encrypted at rest and never
            displayed back to you.
          </p>
        </div>
        <Button onClick={() => setAddOpen(true)}>
          <Plus size={16} /> Add provider
        </Button>
      </div>

      <Tabs defaultValue="providers">
        <TabsList>
          <TabsTrigger value="providers">Providers</TabsTrigger>
          <TabsTrigger value="usage">Usage</TabsTrigger>
        </TabsList>

        <TabsContent value="providers">
          <Card>
            <CardHeader>
              <CardTitle>Configured providers</CardTitle>
              <CardDescription>
                One default at a time. Click &ldquo;Test&rdquo; to verify connectivity.
              </CardDescription>
            </CardHeader>
            <CardContent>
              {isLoading ? (
                <div className="text-sm text-slate">Loading…</div>
              ) : !providers || providers.length === 0 ? (
                <div className="text-sm text-slate py-8 text-center">
                  No providers yet. Click &ldquo;Add provider&rdquo; to configure one.
                </div>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Provider</TableHead>
                      <TableHead>Model</TableHead>
                      <TableHead>Label</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead>Default</TableHead>
                      <TableHead className="text-right">Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {providers.map((p) => (
                      <ProviderRow
                        key={p.id}
                        p={p}
                        onSetDefault={() => setDefault.mutate(p.id)}
                        onTest={() => test.mutate(p.id)}
                        onRemove={() => {
                          if (confirm(`Remove ${p.displayName}?`)) remove.mutate(p.id);
                        }}
                        busy={
                          setDefault.isPending || test.isPending || remove.isPending
                        }
                      />
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="usage">
          <UsageTab />
        </TabsContent>
      </Tabs>

      <AddProviderDialog
        open={addOpen}
        onClose={() => setAddOpen(false)}
        onSubmit={(v) => create.mutate(v)}
        submitting={create.isPending}
        existingDefault={Boolean(providers?.some((p) => p.isDefault))}
      />
    </div>
  );
}

function ProviderRow({
  p,
  onSetDefault,
  onTest,
  onRemove,
  busy,
}: {
  p: LlmProviderResponse;
  onSetDefault: () => void;
  onTest: () => void;
  onRemove: () => void;
  busy: boolean;
}) {
  const label = PROVIDERS.find((x) => x.value === p.provider)?.label ?? p.provider;
  return (
    <TableRow>
      <TableCell className="font-medium">{label}</TableCell>
      <TableCell className="text-slate">{p.modelName}</TableCell>
      <TableCell>{p.displayName}</TableCell>
      <TableCell>
        {p.status === "ACTIVE" ? (
          <span className="inline-flex items-center gap-1 text-emerald-700 text-xs font-semibold">
            <CheckCircle2 size={14} /> Active
          </span>
        ) : p.status === "KEY_INVALID" ? (
          <span className="inline-flex items-center gap-1 text-burgundy text-xs font-semibold">
            <AlertTriangle size={14} /> Key invalid
          </span>
        ) : (
          <span className="text-slate text-xs font-semibold">Disabled</span>
        )}
        {p.lastValidatedAt && (
          <div className="text-[10px] text-ash mt-0.5">
            checked {new Date(p.lastValidatedAt).toLocaleString()}
          </div>
        )}
      </TableCell>
      <TableCell>
        {p.isDefault ? (
          <span className="inline-flex items-center gap-1 text-gold-deep text-xs font-semibold">
            <Star size={14} /> Default
          </span>
        ) : (
          <button
            onClick={onSetDefault}
            disabled={busy}
            className="inline-flex items-center gap-1 text-xs text-slate hover:text-charcoal disabled:opacity-50"
          >
            <StarOff size={14} /> Set default
          </button>
        )}
      </TableCell>
      <TableCell className="text-right">
        <div className="inline-flex gap-2">
          <Button size="sm" variant="secondary" onClick={onTest} disabled={busy}>
            <Plug size={14} /> Test
          </Button>
          <Button size="sm" variant="danger" onClick={onRemove} disabled={busy}>
            <Trash2 size={14} />
          </Button>
        </div>
      </TableCell>
    </TableRow>
  );
}

function AddProviderDialog({
  open,
  onClose,
  onSubmit,
  submitting,
  existingDefault,
}: {
  open: boolean;
  onClose: () => void;
  onSubmit: (v: FormValues) => void;
  submitting: boolean;
  existingDefault: boolean;
}) {
  const {
    register,
    handleSubmit,
    formState: { errors },
    watch,
    setValue,
    reset,
  } = useForm<FormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      provider: "ANTHROPIC",
      modelName: PROVIDERS[0].defaultModel,
      displayName: "",
      apiKey: "",
      endpointOverride: "",
      isDefault: !existingDefault,
    },
  });

  const provider = watch("provider");
  const meta = PROVIDERS.find((p) => p.value === provider) ?? PROVIDERS[0];

  return (
    <Dialog
      open={open}
      onOpenChange={(v) => {
        if (!v) {
          onClose();
          reset();
        }
      }}
    >
      <DialogContent>
        <form
          onSubmit={handleSubmit((v) => {
            onSubmit(v);
            reset();
          })}
        >
          <DialogHeader>
            <DialogTitle>Add LLM provider</DialogTitle>
          </DialogHeader>
          <DialogBody className="space-y-4">
            <Field>
              <Label>Provider</Label>
              <select
                {...register("provider")}
                onChange={(e) => {
                  const p = PROVIDERS.find((x) => x.value === e.target.value);
                  if (p) setValue("modelName", p.defaultModel);
                }}
                className="h-10 w-full rounded-[10px] border border-divider bg-paper px-3.5 text-sm text-charcoal"
              >
                {PROVIDERS.map((p) => (
                  <option key={p.value} value={p.value}>
                    {p.label}
                  </option>
                ))}
              </select>
              {errors.provider && <FieldError>{errors.provider.message}</FieldError>}
            </Field>

            <Field>
              <Label>Model</Label>
              <Input {...register("modelName")} placeholder={meta.defaultModel} />
              {errors.modelName && <FieldError>{errors.modelName.message}</FieldError>}
            </Field>

            <Field>
              <Label>Label (so you can tell them apart)</Label>
              <Input
                {...register("displayName")}
                placeholder={`My ${meta.label} key`}
              />
              {errors.displayName && (
                <FieldError>{errors.displayName.message}</FieldError>
              )}
            </Field>

            {meta.needsKey && (
              <Field>
                <Label>API key</Label>
                <Input
                  type="password"
                  autoComplete="off"
                  {...register("apiKey")}
                />
              </Field>
            )}

            <Field>
              <Label>Endpoint override (optional)</Label>
              <Input
                {...register("endpointOverride")}
                placeholder={
                  provider === "OLLAMA"
                    ? "http://host.docker.internal:11434"
                    : provider === "AZURE_OPENAI"
                    ? "https://<your-resource>.openai.azure.com"
                    : "leave blank for provider default"
                }
              />
            </Field>

            <Field>
              <label className="inline-flex items-center gap-2 text-sm text-charcoal">
                <input type="checkbox" {...register("isDefault")} />
                Make this my default provider
              </label>
            </Field>
          </DialogBody>
          <DialogFooter>
            <Button type="button" variant="secondary" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" disabled={submitting}>
              {submitting ? "Saving…" : "Add provider"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
