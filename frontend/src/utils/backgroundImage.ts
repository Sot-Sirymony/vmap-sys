/**
 * Turns a picked file into a data URL sized for use as the app background.
 *
 * Downscaled to `maxDimension` and re-encoded as JPEG before storing: the
 * image lives in localStorage (device-local, never uploaded), and a photo
 * straight off a camera would blow through the quota. JPEG rather than the
 * original format because a background sits behind a scrim — compression
 * artefacts are invisible there, and the size difference is what makes the
 * feature fit in storage at all.
 */
export async function fileToBackgroundDataUrl(file: File, maxDimension = 1920, quality = 0.85): Promise<string> {
  const bitmap = await createImageBitmap(file);
  try {
    const scale = Math.min(1, maxDimension / Math.max(bitmap.width, bitmap.height));
    const canvas = document.createElement('canvas');
    canvas.width = Math.max(1, Math.round(bitmap.width * scale));
    canvas.height = Math.max(1, Math.round(bitmap.height * scale));
    const context = canvas.getContext('2d');
    if (!context) {
      throw new Error('Canvas unavailable.');
    }
    context.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    return canvas.toDataURL('image/jpeg', quality);
  } finally {
    bitmap.close();
  }
}

/**
 * localStorage practically caps around 5MB per origin; past this even the
 * compressed image will not survive a reload, so the picker refuses it with
 * an explanation instead of appearing to work once.
 */
export const MAX_BACKGROUND_DATA_URL_LENGTH = 4_500_000;
