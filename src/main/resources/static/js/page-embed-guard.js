(() => {
  if (window.top === window.self) {
    const next = `/Full_Compact.html${window.location.search || ""}`;
    window.location.replace(next);
  }
})();
