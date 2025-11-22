// Basic UI interactions for the upload page (pagination + selections + nav toggle).
document.addEventListener("DOMContentLoaded", () => {
    const navToggle = document.getElementById("navToggle");
    const navLinks = document.getElementById("navLinks");
    navToggle?.addEventListener("click", () => {
        navLinks?.classList.toggle("is-open");
        const expanded = navLinks?.classList.contains("is-open");
        navToggle.setAttribute("aria-expanded", expanded ? "true" : "false");
    });

    const table = document.getElementById("transactionsTable");
    if (!table) {
        return;
    }

    const rows = Array.from(table.querySelectorAll("tbody tr"));
    const pageSize = 10;
    let currentPage = 1;
    const pageInfo = document.getElementById("pageInfo");
    const prevBtn = document.getElementById("prevPage");
    const nextBtn = document.getElementById("nextPage");

    const renderPage = () => {
        const totalPages = Math.max(1, Math.ceil(rows.length / pageSize));
        if (currentPage > totalPages) {
            currentPage = totalPages;
        }
        rows.forEach((row, index) => {
            const start = (currentPage - 1) * pageSize;
            const end = start + pageSize;
            row.style.display = index >= start && index < end ? "" : "none";
        });
        if (pageInfo) {
            pageInfo.textContent = `Page ${currentPage} / ${totalPages}`;
        }
        if (prevBtn) {
            prevBtn.disabled = currentPage === 1;
        }
        if (nextBtn) {
            nextBtn.disabled = currentPage === totalPages;
        }
    };

    prevBtn?.addEventListener("click", () => {
        if (currentPage > 1) {
            currentPage--;
            renderPage();
        }
    });
    nextBtn?.addEventListener("click", () => {
        const totalPages = Math.max(1, Math.ceil(rows.length / pageSize));
        if (currentPage < totalPages) {
            currentPage++;
            renderPage();
        }
    });
    renderPage();

    const selectAll = document.getElementById("selectAll");
    const exportBtn = document.getElementById("exportBtn");
    const rowCheckboxes = Array.from(table.querySelectorAll('tbody input[type="checkbox"]'));

    const updateExportState = () => {
        const hasSelection = rowCheckboxes.some(cb => cb.checked);
        if (exportBtn) {
            exportBtn.disabled = !hasSelection;
        }
    };

    selectAll?.addEventListener("change", (event) => {
        const checked = event.target.checked;
        rowCheckboxes.forEach(cb => {
            cb.checked = checked;
        });
        updateExportState();
    });

    rowCheckboxes.forEach(cb => cb.addEventListener("change", () => {
        if (!cb.checked && selectAll) {
            selectAll.checked = false;
        }
        updateExportState();
    }));

    updateExportState();
});
