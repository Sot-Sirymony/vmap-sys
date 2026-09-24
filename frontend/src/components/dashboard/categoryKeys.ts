/**
 * The key the breakdown charts use for their folded "everything else" slice.
 *
 * It lives apart from the chart that produces it so a caller can compare
 * against it — the dashboard links that slice somewhere different — without
 * importing the chart, and with it Recharts, into its own bundle.
 */
export const OTHER_CATEGORY_KEY = 'OTHER_CATEGORIES';
