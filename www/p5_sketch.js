(() => {
  try {
    const code = window.Android?.getSketchCode?.(window.__editRinRunToken) || '';
    const script = document.createElement('script');
    script.id = 'user-sketch';
    script.textContent = `${code}\n//# sourceURL=sketch.js?run=${window.__editRinRunToken}`;
    document.head.appendChild(script);
  } catch (error) {
    window.__editKiroReportError(error?.message || String(error));
  }
})();
