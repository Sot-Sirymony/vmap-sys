import { afterEach, describe, expect, it, vi } from 'vitest';
import { downloadManagementReport } from './reportApi';

describe('downloadManagementReport (V18 MR-FR-15)', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('asks for the scope and language, and takes the file name from the server', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(new Blob(['x']), {
      status: 200,
      headers: { 'Content-Disposition': 'attachment; filename="Management_Report_Area_3_2026-11-16.xlsx"' },
    }));
    vi.stubGlobal('fetch', fetchMock);
    const result = await downloadManagementReport('token', { areaId: 3 }, 'xlsx', 'km');
    expect(String(fetchMock.mock.calls[0][0])).toMatch(/\/reports\/management\.xlsx\?areaId=3&language=km$/);
    expect(result.fileName).toBe('Management_Report_Area_3_2026-11-16.xlsx');
  });

  it('sends the deck title and leaves out the name only when asked (MR-FR-31)', async () => {
    const fetchMock = vi.fn().mockImplementation(async () => new Response(new Blob(['x']), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    await downloadManagementReport('token', { dreamId: 7 }, 'pptx', 'en', { title: ' Quarterly review ', includeName: false });
    expect(String(fetchMock.mock.calls[0][0])).toMatch(/management\.pptx\?dreamId=7&language=en&title=Quarterly%20review&includeName=false$/);
    await downloadManagementReport('token', {}, 'pptx', 'en', { title: '  ', includeName: true });
    expect(String(fetchMock.mock.calls[1][0])).toMatch(/management\.pptx\?language=en$/);
    // Deck options never reach the Excel request.
    await downloadManagementReport('token', {}, 'xlsx', 'en', { title: 'Ignored', includeName: false });
    expect(String(fetchMock.mock.calls[2][0])).toMatch(/management\.xlsx\?language=en$/);
  });

  it('sends the period with the scope, leaving out custom dates for a preset (V19 RM-FR-06)', async () => {
    const fetchMock = vi.fn().mockImplementation(async () => new Response(new Blob(['x']), { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    await downloadManagementReport('token', { dreamId: 7, period: 'CUSTOM', from: '2026-11-01', to: '2026-11-10' }, 'xlsx', 'en');
    expect(String(fetchMock.mock.calls[0][0])).toMatch(/management\.xlsx\?dreamId=7&period=CUSTOM&from=2026-11-01&to=2026-11-10&language=en$/);
    await downloadManagementReport('token', { period: 'LAST_MONTH', from: '2026-11-01', to: '2026-11-10' }, 'xlsx', 'en');
    expect(String(fetchMock.mock.calls[1][0])).toMatch(/management\.xlsx\?period=LAST_MONTH&language=en$/);
  });

  it('uses the server\'s message when the download is refused', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ message: 'Dream not found' }), { status: 404 })));
    await expect(downloadManagementReport('token', { dreamId: 9 }, 'xlsx', 'en')).rejects.toThrow('Dream not found');
  });

  it('falls back to the status when the error is not JSON, and to a default file name', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('oops', { status: 500 })));
    await expect(downloadManagementReport('token', {}, 'xlsx', 'en')).rejects.toThrow('Download failed with status 500');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(new Blob(['x']), { status: 200 })));
    expect((await downloadManagementReport('token', {}, 'xlsx', 'en')).fileName).toBe('Management_Report.xlsx');
  });
});
