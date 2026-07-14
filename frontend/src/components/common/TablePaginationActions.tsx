import IconButton from '@mui/material/IconButton';
import Box from '@mui/material/Box';
import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from 'lucide-react';
import type { TablePaginationActionsProps } from '@mui/material/TablePaginationActions';

/**
 * Pagination actions for MUI TablePagination: first, previous, next, and last
 * page. The default TablePagination only offers previous/next, which makes the
 * end of a long list hard to reach.
 */
export function TablePaginationActions({ count, page, rowsPerPage, onPageChange }: TablePaginationActionsProps) {
  const lastPage = Math.max(0, Math.ceil(count / rowsPerPage) - 1);

  return (
    <Box sx={{ flexShrink: 0, ml: 2.5, display: 'flex' }}>
      <IconButton
        onClick={(event) => onPageChange(event, 0)}
        disabled={page === 0}
        aria-label="First page"
        size="small"
      >
        <ChevronsLeft size={18} />
      </IconButton>
      <IconButton
        onClick={(event) => onPageChange(event, page - 1)}
        disabled={page === 0}
        aria-label="Previous page"
        size="small"
      >
        <ChevronLeft size={18} />
      </IconButton>
      <IconButton
        onClick={(event) => onPageChange(event, page + 1)}
        disabled={page >= lastPage}
        aria-label="Next page"
        size="small"
      >
        <ChevronRight size={18} />
      </IconButton>
      <IconButton
        onClick={(event) => onPageChange(event, lastPage)}
        disabled={page >= lastPage}
        aria-label="Last page"
        size="small"
      >
        <ChevronsRight size={18} />
      </IconButton>
    </Box>
  );
}
