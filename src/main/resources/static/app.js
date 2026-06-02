document.addEventListener('DOMContentLoaded', async () => {
    await loadDictionary();
    loadSessions();
    loadTemplates();
    setupReplayThrottle();
    addTagRow("35", "D"); // Add default MsgType=NewOrderSingle to the builder
    startMessagePolling();
    loadFeatureFiles();
    startSystemLogPolling();
});

let activeSessions = [];
let messagePollInterval;
let lastMessageId = 0;
let sessionColorMap = {};
let colorIndex = 0;
let messageTemplates = [];

const sessionColors = [
    'table-light',
    'table-secondary',
    'table-primary',
    'table-info',
    'table-warning',
    'table-success',
    'table-danger'
];

let fixDictionary = {};

async function loadDictionary(version = '') {
    try {
        const url = version ? `/api/dictionary?version=${encodeURIComponent(version)}` : '/api/dictionary';
        const res = await fetch(url);
        fixDictionary = await res.json();
    } catch (err) {
        console.error('Failed to load dictionary', err);
    }
}

function startMessagePolling() {
    if (messagePollInterval) clearInterval(messagePollInterval);
    messagePollInterval = setInterval(fetchMessages, 1000);
}

async function fetchMessages() {
    try {
        const res = await fetch('/api/sessions/messages?since=' + lastMessageId);
        const messages = await res.json();
        
        if (messages.length === 0) return;
        
        const tbody = document.querySelector('#messageLogTable tbody');
        let addedNew = false;

        messages.forEach(msg => {
            if (msg.id > lastMessageId) {
                lastMessageId = msg.id;
            }
            
            if (!sessionColorMap[msg.session]) {
                sessionColorMap[msg.session] = sessionColors[colorIndex % sessionColors.length];
                colorIndex++;
            }
            
            const rowColorClass = sessionColorMap[msg.session];
            const directionBadge = msg.direction === 'IN' 
                ? '<span class="badge bg-success">IN</span>' 
                : '<span class="badge bg-primary">OUT</span>';

            const time = new Date(msg.timestamp).toLocaleTimeString();
            
            const isAdmin = /(?:^|\|)35=[012345A]\|/.test(msg.message);
            const hideAdmin = document.getElementById('filterHeartbeatsCheck')?.checked;
            const logFilterVal = document.getElementById('logSessionFilter')?.value;

            const tr = document.createElement('tr');
            tr.className = rowColorClass + (isAdmin ? ' admin-msg' : '');
            tr.setAttribute('data-session', msg.session);
            tr.style.cursor = 'pointer';
            if ((isAdmin && hideAdmin) || (logFilterVal && msg.session !== logFilterVal)) {
                tr.style.display = 'none';
            }
            tr.onclick = () => handleMessageClick(msg.message, tr);
            tr.innerHTML = `
                <td class="text-nowrap">${time}</td>
                <td class="text-nowrap">${msg.session}</td>
                <td>${directionBadge}</td>
                <td style="word-break: break-all; font-family: monospace; font-size: 0.85em;">
                    <span title="View Details" class="view-details-icon" style="cursor: pointer; font-size: 1.1em; margin-right: 8px;">&#128269;</span>${msg.message}
                </td>
            `;
            
            const icon = tr.querySelector('.view-details-icon');
            icon.onclick = (event) => showFixMessageDetails(event, msg.message, msg.session);
            
            tbody.appendChild(tr);
            addedNew = true;
        });
        
        if (addedNew) {
            const container = document.getElementById('messageLogContainer');
            container.scrollTop = container.scrollHeight;
        }
        
    } catch (e) {
        console.error("Failed to fetch messages", e);
    }
}

function setupHeartbeatFilter() {
    const container = document.getElementById('messageLogContainer');
    if (container && container.parentNode) {
        const filterDiv = document.createElement('div');
        filterDiv.className = 'form-check mb-2';
        filterDiv.innerHTML = `
            <input class="form-check-input" type="checkbox" id="filterHeartbeatsCheck" onchange="toggleHeartbeats()">
            <label class="form-check-label fw-bold" for="filterHeartbeatsCheck">
                Hide Heartbeat Messages (35=0)
            </label>
        `;
        container.parentNode.insertBefore(filterDiv, container);
    }
}

function setupReplayThrottle() {
    const replayJson = document.getElementById('replayJson');
    if (replayJson && replayJson.parentNode) {
        const throttleDiv = document.createElement('div');
        throttleDiv.className = 'mb-2 d-flex align-items-center gap-3';
        throttleDiv.innerHTML = `
            <div class="d-flex align-items-center">
                <label class="form-label fw-bold mb-0 me-2 text-nowrap" for="replayThrottleMs">Throttling (ms):</label>
                <input type="number" id="replayThrottleMs" class="form-control" value="100" min="0" style="width: 100px;">
            </div>
            <div class="d-flex align-items-center">
                <label class="form-label fw-bold mb-0 me-2 text-nowrap" for="replayRepeat">Repeat Times:</label>
                <input type="number" id="replayRepeat" class="form-control" value="1" min="1" style="width: 100px;">
            </div>
        `;
        replayJson.parentNode.insertBefore(throttleDiv, replayJson);
    }
}

function toggleHeartbeats() {
    const hide = document.getElementById('filterHeartbeatsCheck').checked;
    const rows = document.querySelectorAll('#messageLogTable tbody tr.heartbeat-msg');
    rows.forEach(row => {
        row.style.display = hide ? 'none' : '';
    });
}

async function loadSessions() {
    try {
        const res = await fetch('/api/sessions');
        activeSessions = await res.json();
        
        const tbody = document.querySelector('#sessionTable tbody');
        const selects = [document.getElementById('sendSessionSelect'), document.getElementById('replaySessionSelect')];
        
        const logFilter = document.getElementById('logSessionFilter');
        const currentLogFilterVal = logFilter ? logFilter.value : '';
        if (logFilter) logFilter.innerHTML = '<option value="">-- All Sessions --</option>';

        tbody.innerHTML = '';
        selects.forEach(sel => sel.innerHTML = '');

        activeSessions.forEach((session, i) => {
            // Populate Table
            const isLogged = session.isLoggedOn;
            const badge = isLogged ? '<span class="badge bg-success">Logged On</span>' : '<span class="badge bg-danger">Disconnected</span>';
            
            tbody.innerHTML += `
                <tr>
                    <td class="align-middle" style="font-family: monospace;">${session.sessionString}</td>
                    <td class="align-middle">${badge}</td>
                    <td class="align-middle">
                        <input type="number" class="form-control form-control-sm px-1" id="inSeqInput-${i}" value="${session.inSeq}" style="width: 80px;" ${isLogged ? 'disabled' : ''}>
                    </td>
                    <td class="align-middle">
                        <input type="number" class="form-control form-control-sm px-1" id="outSeqInput-${i}" value="${session.outSeq}" style="width: 80px;" ${isLogged ? 'disabled' : ''}>
                    </td>
                    <td class="align-middle">
                        <div class="btn-group btn-group-sm">
                            <button class="btn btn-outline-secondary" onclick="setSequence('${session.sessionString}', ${i})" title="Set sequence numbers" ${isLogged ? 'disabled' : ''}>Set</button>
                            <button class="btn btn-outline-primary" onclick="sessionAction('${session.sessionString}', 'logon')" ${isLogged ? 'disabled' : ''}>Logon</button>
                            <button class="btn btn-outline-warning" onclick="sessionAction('${session.sessionString}', 'logout')" ${!isLogged ? 'disabled' : ''}>Logout</button>
                            <button class="btn btn-outline-danger" onclick="sessionAction('${session.sessionString}', 'reset')" title="Reset sequence numbers to 1" ${isLogged ? 'disabled' : ''}>Reset</button>
                        </div>
                    </td>
                </tr>
            `;

            // Populate Dropdowns
            const opt = new Option(session.sessionString, session.sessionString);
            selects.forEach(sel => sel.add(opt.cloneNode(true)));
            if (logFilter) logFilter.add(opt.cloneNode(true));
        });

        if (logFilter && currentLogFilterVal) logFilter.value = currentLogFilterVal;

        if (selects[0] && selects[0].value) {
            updateDictionaryForSession(selects[0].value);
        }
        
        selects.forEach(sel => {
            sel.onchange = (e) => updateDictionaryForSession(e.target.value);
        });
    } catch (err) {
        console.error('Failed to load sessions', err);
    }
}

async function updateDictionaryForSession(sessionString) {
    if (!sessionString) return;
    const parts = sessionString.split(':');
    if (parts.length > 0) {
        await loadDictionary(parts[0]);
        document.querySelectorAll('.fix-tag').forEach(input => updateTagLabel(input));
    }
}

async function setSequence(sessionString, index) {
    const inSeqEl = document.getElementById(`inSeqInput-${index}`);
    const outSeqEl = document.getElementById(`outSeqInput-${index}`);

    const payload = {};
    if (inSeqEl.value) payload.inSeq = parseInt(inSeqEl.value, 10);
    if (outSeqEl.value) payload.outSeq = parseInt(outSeqEl.value, 10);

    if (Object.keys(payload).length === 0) {
        return alert("Please provide at least one sequence number to set.");
    }

    try {
        const res = await fetch(`/api/sessions/${encodeURIComponent(sessionString)}/setseq`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        const responseText = await res.text();
        alert(responseText);
        if (res.ok) {
            setTimeout(loadSessions, 200);
        }
    } catch (e) {
        alert("An error occurred: " + e.message);
    }
}

async function sessionAction(sessionString, action) {
    await fetch(`/api/sessions/${encodeURIComponent(sessionString)}/${action}`, { method: 'POST' });
    setTimeout(loadSessions, 500); // give the engine time to connect/disconnect before reloading UI
}

function addTagRow(defaultTag = "", defaultValue = "") {
    const row = document.createElement('div');
    row.className = 'input-group mb-2 w-100 tag-row';
    const desc = fixDictionary[defaultTag] ? ` (${fixDictionary[defaultTag]})` : '';
    
    row.innerHTML = `
        <span class="input-group-text tag-label" style="width: 170px; display: inline-block; text-align: left; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">Tag${desc}</span>
        <input type="text" class="form-control fix-tag" placeholder="e.g. 35" value="${defaultTag}" oninput="updateTagLabel(this)">
        <span class="input-group-text">Value</span>
        <input type="text" class="form-control fix-val" placeholder="e.g. D" value="${defaultValue}">
        <button class="btn btn-outline-danger" onclick="this.parentElement.remove()">X</button>
    `;
    document.getElementById('tagRows').appendChild(row);
}

function updateTagLabel(input) {
    const tag = input.value.trim();
    const label = input.parentElement.querySelector('.tag-label');
    const desc = fixDictionary[tag] ? ` (${fixDictionary[tag]})` : '';
    label.innerText = `Tag${desc}`;
}

async function loadTemplates() {
    try {
        const res = await fetch('/api/templates');
        messageTemplates = await res.json();
        
        let templateSelect = document.getElementById('templateSelect');
        if (!templateSelect) {
            const tagRows = document.getElementById('tagRows');
            if (tagRows) {
                const templateContainer = document.createElement('div');
                templateContainer.className = 'mb-3';
                templateContainer.innerHTML = `
                    <label class="form-label fw-bold">Message Template</label>
                    <select id="templateSelect" class="form-select" onchange="applyTemplate()">
                        <option value="">-- Select Template --</option>
                    </select>
                `;
                // Inject right before the tags section
                tagRows.parentNode.insertBefore(templateContainer, tagRows);
                templateSelect = document.getElementById('templateSelect');
            }
        }
        
        if (templateSelect) {
            templateSelect.innerHTML = '<option value="">-- Select Template --</option>';
            messageTemplates.forEach((tpl, idx) => {
                const opt = new Option(tpl.name, idx);
                templateSelect.add(opt);
            });
        }
    } catch (err) {
        console.error('Failed to load templates', err);
    }
}

function applyTemplate() {
    const templateIdx = document.getElementById('templateSelect').value;
    if (templateIdx === "") return;
    
    const template = messageTemplates[templateIdx];
    
    const tagRows = document.getElementById('tagRows');
    tagRows.innerHTML = '';
    
    if (template && template.tags) {
        for (const [tag, value] of Object.entries(template.tags)) {
            addTagRow(tag, value);
        }
    }
}

function generateFixTimestamp() {
    const d = new Date();
    const pad = (n, width = 2) => String(n).padStart(width, '0');
    const date = `${d.getUTCFullYear()}${pad(d.getUTCMonth() + 1)}${pad(d.getUTCDate())}`;
    const time = `${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}:${pad(d.getUTCSeconds())}.${pad(d.getUTCMilliseconds(), 3)}`;
    return `${date}-${time}`;
}

async function sendMessage() {
    const targetSession = document.getElementById('sendSessionSelect').value;
    if (!targetSession) return alert("Select a target session first.");

    const tagMap = {};
    document.querySelectorAll('.tag-row').forEach(row => {
        const tagInput = row.querySelector('.fix-tag');
        const valInput = row.querySelector('.fix-val');
        const tag = tagInput.value.trim();
        let val = valInput.value.trim();
        
        if (tag === "11") {
            val = "ORD_" + Date.now();
            valInput.value = val; // Update the UI field so the user sees what was actually sent
        } else if (tag === "60") {
            val = generateFixTimestamp();
            valInput.value = val; // Update the UI field so the user sees what was actually sent
        }
        
        if (tag && val) tagMap[tag] = val;
    });

    const res = await fetch(`/api/sessions/${encodeURIComponent(targetSession)}/send`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(tagMap)
    });
    
    if (!res.ok) alert("Error sending message: " + await res.text());
}

function handleMessageClick(rawMessage, rowElement) {
    // 1. Visual feedback that the row was clicked
    if (rowElement) {
        const origFilter = rowElement.style.filter;
        rowElement.style.filter = 'brightness(85%)';
        setTimeout(() => rowElement.style.filter = origFilter, 300);
    }

    const tagMap = {};
    // 2. Map Tag 11 from the message to Tag 41 in the template (OrigClOrdID)
    const match11 = rawMessage.match(/(?:^|\|)11=([^|]+)/);
    if (match11 && match11[1]) tagMap["41"] = match11[1];
    
    // 3. Extract Symbol (55) and OrderQty (38) to auto-fill Cancel/Replace templates as well
    const match55 = rawMessage.match(/(?:^|\|)55=([^|]+)/);
    if (match55 && match55[1]) tagMap["55"] = match55[1];
    
    const match38 = rawMessage.match(/(?:^|\|)38=([^|]+)/);
    if (match38 && match38[1]) tagMap["38"] = match38[1];

    if (Object.keys(tagMap).length > 0) {
        document.querySelectorAll('.tag-row').forEach(row => {
            const tagInput = row.querySelector('.fix-tag');
            const valInput = row.querySelector('.fix-val');
            if (tagInput && valInput) {
                const tag = tagInput.value.trim();
                if (tagMap[tag]) {
                    valInput.value = tagMap[tag];
                    
                    // 4. Highlight the updated input briefly so it's obvious it was filled
                    const origBg = valInput.style.backgroundColor;
                    valInput.style.backgroundColor = '#d1e7dd'; // light green
                    setTimeout(() => { valInput.style.backgroundColor = origBg; }, 600);
                }
            }
        });
    }
}

async function showFixMessageDetails(event, rawMessage, sessionString) {
    event.stopPropagation(); // Prevent the row click event from firing and autofilling tags

    let localDictionary = fixDictionary;
    if (sessionString) {
        const parts = sessionString.split(':');
        if (parts.length > 0) {
            try {
                const res = await fetch(`/api/dictionary?version=${encodeURIComponent(parts[0])}`);
                localDictionary = await res.json();
            } catch (err) {
                console.error("Failed to load dictionary for message", err);
            }
        }
    }

    let popup = document.getElementById('fixDetailsPopup');
    let backdrop = document.getElementById('fixDetailsBackdrop');

    if (!popup) {
        backdrop = document.createElement('div');
        backdrop.id = 'fixDetailsBackdrop';
        backdrop.style.position = 'fixed';
        backdrop.style.top = '0';
        backdrop.style.left = '0';
        backdrop.style.width = '100vw';
        backdrop.style.height = '100vh';
        backdrop.style.backgroundColor = 'rgba(0,0,0,0.5)';
        backdrop.style.zIndex = '1040';
        backdrop.onclick = () => {
            popup.style.display = 'none';
            backdrop.style.display = 'none';
        };
        document.body.appendChild(backdrop);

        popup = document.createElement('div');
        popup.id = 'fixDetailsPopup';
        popup.className = 'card shadow';
        popup.style.position = 'fixed';
        popup.style.top = '50%';
        popup.style.left = '50%';
        popup.style.transform = 'translate(-50%, -50%)';
        popup.style.backgroundColor = 'white';
        popup.style.zIndex = '1050';
        popup.style.maxHeight = '85vh';
        popup.style.width = '90vw';
        popup.style.maxWidth = '600px';
        popup.style.display = 'flex';
        popup.style.flexDirection = 'column';

        document.body.appendChild(popup);
    }
    
    popup.style.display = 'flex';
    backdrop.style.display = 'block';

    let html = `
        <div class="card-header d-flex justify-content-between align-items-center bg-light">
            <h5 class="m-0">FIX Message Details</h5>
            <button type="button" class="btn-close" onclick="document.getElementById('fixDetailsPopup').style.display='none'; document.getElementById('fixDetailsBackdrop').style.display='none';"></button>
        </div>
        <div class="card-body p-0" style="overflow-y: auto;">
            <table class="table table-sm table-bordered table-striped mb-0">
                <thead class="table-light" style="position: sticky; top: 0; z-index: 1;">
                    <tr>
                        <th class="ps-3" style="width: 20%;">Tag</th>
                        <th style="width: 40%;">Description</th>
                        <th style="width: 40%;">Value</th>
                    </tr>
                </thead>
                <tbody>
    `;

    const fields = rawMessage.split('|');
    fields.forEach(field => {
        if (!field) return;
        const parts = field.split('=');
        if (parts.length === 2) {
            const tag = parts[0];
            const value = parts[1];
            const desc = localDictionary[tag] || '';
            html += `
                <tr>
                    <td class="fw-bold ps-3">${tag}</td>
                    <td>${desc}</td>
                    <td style="font-family: monospace; word-break: break-all;">${value}</td>
                </tr>
            `;
        }
    });

    html += `
                </tbody>
            </table>
        </div>
    `;
    popup.innerHTML = html;
}

async function replayMessages() {
    const targetSession = document.getElementById('replaySessionSelect').value;
    let payload;
    
    try {
        payload = JSON.parse(document.getElementById('replayJson').value);
    } catch (e) {
        return alert("Invalid JSON format in replay window.");
    }

    let throttle = 100;
    const throttleInput = document.getElementById('replayThrottleMs');
    if (throttleInput) {
        throttle = parseInt(throttleInput.value, 10) || 0;
    }

    let repeat = 1;
    const repeatInput = document.getElementById('replayRepeat');
    if (repeatInput) {
        repeat = parseInt(repeatInput.value, 10) || 1;
    }

    const res = await fetch(`/api/sessions/${encodeURIComponent(targetSession)}/replay?throttle=${throttle}&repeat=${repeat}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    });
    
    if (res.ok) alert(await res.text());
    else alert("Replay failed: " + await res.text());
}

async function loadFeatureFiles() {
    const container = document.getElementById('featureCheckboxes');
    if (!container) return;
    
    container.innerHTML = '<span class="text-muted">Loading feature files...</span>';

    try {
        const res = await fetch('/api/tests/features');
        if (!res.ok) throw new Error(`HTTP Error: ${res.status}`);
        const features = await res.json();
        
        container.innerHTML = '';
        if (features.length === 0) {
            container.innerHTML = '<span class="text-muted">No .feature files found in the "features/" directory alongside the binary.</span>';
            return;
        }
        
        features.forEach(feature => {
            const div = document.createElement('div');
            div.className = 'form-check';
            div.innerHTML = `
                <input class="form-check-input feature-checkbox" type="checkbox" value="${feature}" id="chk_${feature.replace(/[^a-zA-Z0-9]/g, '_')}">
                <label class="form-check-label" for="chk_${feature.replace(/[^a-zA-Z0-9]/g, '_')}">${feature}</label>
            `;
            container.appendChild(div);
        });
    } catch (err) {
        console.error('Failed to load features', err);
        container.innerHTML = `<span class="text-danger">Error loading feature files. Check console (${err.message})</span>`;
    }
}

function selectAllFeatures() {
    document.querySelectorAll('.feature-checkbox').forEach(cb => cb.checked = true);
}

function deselectAllFeatures() {
    document.querySelectorAll('.feature-checkbox').forEach(cb => cb.checked = false);
}

async function runTests() {
    const pre = document.getElementById('testOutput');
    const selectedFeatures = Array.from(document.querySelectorAll('.feature-checkbox:checked')).map(cb => cb.value);
    
    if (document.querySelectorAll('.feature-checkbox').length > 0 && selectedFeatures.length === 0) {
        return alert("Please select at least one feature file to run.");
    }

    const featureParam = selectedFeatures.length > 0 ? `?feature=${encodeURIComponent(selectedFeatures.join(','))}` : '';
    
    pre.textContent = "Running tests... Please wait.\n";
    
    try {
        const res = await fetch(`/api/tests/run${featureParam}`, { method: 'POST' });
        if (!res.ok) throw new Error("Request failed: " + res.statusText);
        
        const reader = res.body.getReader();
        const decoder = new TextDecoder('utf-8');
        pre.textContent = ""; // Clear waiting message once stream starts
        
        while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            
            pre.textContent += decoder.decode(value, { stream: true });
            pre.scrollTop = pre.scrollHeight; // Auto-scroll to bottom
        }
    } catch (e) {
        pre.textContent += "\nError triggering tests: " + e;
    }
}

let lastSystemLogId = 0;
let systemLogInterval;

function startSystemLogPolling() {
    if (systemLogInterval) clearInterval(systemLogInterval);
    systemLogInterval = setInterval(fetchSystemLogs, 2000);
}

async function fetchSystemLogs() {
    try {
        const logTab = document.getElementById('system-log-tab');
        if (!logTab || logTab.style.display === 'none') return;

        const res = await fetch('/api/logs?since=' + lastSystemLogId);
        const logs = await res.json();
        
        if (logs.length === 0) return;
        
        const pre = document.getElementById('systemLogOutput');
        let addedNew = false;
        
        logs.forEach(log => {
            if (log.id > lastSystemLogId) {
                lastSystemLogId = log.id;
            }
            pre.textContent += log.message + "\n";
            addedNew = true;
        });
        
        if (addedNew) pre.scrollTop = pre.scrollHeight;
    } catch (e) {
        console.error("Failed to fetch system logs", e);
    }
}

async function clearSystemLogs() {
    await fetch('/api/logs/clear', { method: 'POST' });
    document.getElementById('systemLogOutput').textContent = '';
    lastSystemLogId = 0;
}

// Helper function to handle tab switching
function showTab(tabId, element) {
    // Hide all tab content panes and remove active class
    document.querySelectorAll('.tab-pane').forEach(pane => {
        pane.style.display = 'none';
        pane.classList.remove('active');
    });
    // Remove active state from all navigation links
    document.querySelectorAll('.nav-link').forEach(link => link.classList.remove('active'));

    // Show target tab pane and set menu link as active
    const target = document.getElementById(tabId);
    if (target) {
        target.style.display = 'block';
        target.classList.add('active');
    }
    if (element) {
        element.classList.add('active');
    }
}
