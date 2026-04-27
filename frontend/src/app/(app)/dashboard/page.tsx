import { redirect } from "next/navigation";

/**
 * Singular `/dashboard` is a common misspelling of the root `/` and the plural `/dashboards`.
 * Redirect rather than 404 so bookmarks and shared links keep working.
 */
export default function DashboardAliasPage() {
  redirect("/");
}
