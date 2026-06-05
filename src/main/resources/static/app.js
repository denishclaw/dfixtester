document.addEventListener('DOMContentLoaded', async () => {
    await loadDictionary();
    loadSessions();
    loadTemplates();
    setupReplayThrottle();
    addTagRow("35", "D", 'tagRows'); // Add default MsgType=NewOrderSingle to the builder
    addTagRow("35", "D", 'atdlTagRows');
    startMessagePolling();
    loadFeatureFiles();
    startSystemLogPolling();
    loadAtdlFiles();
    loadAtdlTemplates();
});

let activeSessions = [];
let messagePollInterval;
let lastMessageId = 0;
let sessionColorMap = {};
let colorIndex = 0;
let messageTemplates = [];
let atdlMessageTemplates = [];
let multiOrderTemplates = [];

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
            
            const isHeartbeat = /(?:^|\|)35=0\|/.test(msg.message);
            const hideHeartbeat = document.getElementById('filterHeartbeatsCheck')?.checked;
            const logFilterVal = document.getElementById('logSessionFilter')?.value;

            const tr = document.createElement('tr');
            tr.className = rowColorClass + (isHeartbeat ? ' heartbeat-msg' : '');
            tr.setAttribute('data-session', msg.session);
            tr.style.cursor = 'pointer';
            if ((isHeartbeat && hideHeartbeat) || (logFilterVal && msg.session !== logFilterVal)) {
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

function clearMessageLog() {
    const tbody = document.querySelector('#messageLogTable tbody');
    if (tbody) tbody.innerHTML = '';
}

function filterMessageLog() {
    const selectedSession = document.getElementById('logSessionFilter')?.value;
    const hideHeartbeat = document.getElementById('filterHeartbeatsCheck')?.checked;
    const rows = document.querySelectorAll('#messageLogTable tbody tr');
    
    rows.forEach(row => {
        const isHeartbeat = row.classList.contains('heartbeat-msg');
        const session = row.getAttribute('data-session');
        
        let show = true;
        if (selectedSession && session !== selectedSession) show = false;
        if (hideHeartbeat && isHeartbeat) show = false;
        
        row.style.display = show ? '' : 'none';
    });
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

async function loadSessions() {
    try {
        const res = await fetch('/api/sessions');
        activeSessions = await res.json();
        
        const tbody = document.querySelector('#sessionTable tbody');
        const selects = [
            document.getElementById('sendSessionSelect'), 
            document.getElementById('replaySessionSelect'),
            document.getElementById('atdlTargetSessionSelect')
        ];
        
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

function addTagRow(defaultTag = "", defaultValue = "", containerId = 'tagRows') {
    const row = document.createElement('div');
    row.className = 'input-group mb-2 w-100 tag-row';
    const desc = fixDictionary[defaultTag] ? ` (${fixDictionary[defaultTag]})` : '';
    
    row.innerHTML = `
        <span class="input-group-text tag-label" style="width: 170px; display: inline-block; text-align: left; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">Tag${desc}</span>
        <input type="text" class="form-control fix-tag" placeholder="e.g. 55" value="${defaultTag}" oninput="updateTagLabel(this)">
        <span class="input-group-text">Value</span>
        <input type="text" class="form-control fix-val" placeholder="e.g. AAPL" value="${defaultValue}">
        <button class="btn btn-outline-danger" onclick="this.parentElement.remove()">X</button>
    `;
    document.getElementById(containerId).appendChild(row);
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
                let templateContainer = document.getElementById('templateContainer');
                if (!templateContainer) {
                    templateContainer = document.createElement('div');
                    templateContainer.id = 'templateContainer';
                    templateContainer.className = 'mb-3';
                    templateContainer.innerHTML = `
                        <label class="form-label fw-bold">Message Template</label>
                        <select id="templateSelect" class="form-select" onchange="applyTemplate('single')">
                            <option value="">-- Select Template --</option>
                        </select>
                    `;
                    tagRows.parentNode.insertBefore(templateContainer, tagRows);
                }
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

async function loadAtdlTemplates() {
    try {
        const res = await fetch('/api/templates/atdl');
        atdlMessageTemplates = await res.json();
        
        const templateSelect = document.getElementById('atdlTemplateSelect');
        if (templateSelect) {
            templateSelect.innerHTML = '<option value="">-- Select Template --</option>';
            atdlMessageTemplates.forEach((tpl, idx) => {
                const opt = new Option(tpl.name, idx);
                templateSelect.add(opt);
            });
        }
    } catch (err) {
        console.error('Failed to load ATDL templates', err);
    }
}

async function loadMultiOrderTemplates() {
    try {
        const res = await fetch('/api/templates/multi-order');
        multiOrderTemplates = await res.json();
        
        const templateSelect = document.getElementById('multiOrderTemplateSelect');
        if (templateSelect) {
            templateSelect.innerHTML = '<option value="">-- Select Multi-Order Template --</option>';
            multiOrderTemplates.forEach((tpl, idx) => {
                const name = (tpl.length > 0 && tpl[0].name) ? tpl[0].name : `Template ${idx + 1}`;
                templateSelect.add(new Option(name, idx));
            });
        }
    } catch (err) {
        console.error('Failed to load multi-order templates', err);
    }
}


function applyTemplate(type = 'single') {
    let templateIdx, templates, containerId;

    if (type === 'atdl') {
        templateIdx = document.getElementById('atdlTemplateSelect').value;
        templates = atdlMessageTemplates;
        containerId = 'atdlTagRows';
    } else {
        templateIdx = document.getElementById('templateSelect').value;
        templates = messageTemplates;
        containerId = 'tagRows';
    }

    if (templateIdx === "") return;
    
    const template = templates[templateIdx];
    
    const tagRowsContainer = document.getElementById(containerId);
    if (!tagRowsContainer) {
        return;
    }
    tagRowsContainer.innerHTML = '';
    
    if (template && template.tags) {
        for (const [tag, value] of Object.entries(template.tags)) {
            addTagRow(tag, value, containerId);
        }
    }
}

function applyMultiOrderTemplate() {
    const templateIdx = document.getElementById('multiOrderTemplateSelect').value;
    if (templateIdx === "") return;
    const template = multiOrderTemplates[templateIdx];
    document.getElementById('replayJson').value = JSON.stringify(template, null, 2);
}


function generateFixTimestamp() {
    const d = new Date();
    const pad = (n, width = 2) => String(n).padStart(width, '0');
    const date = `${d.getUTCFullYear()}${pad(d.getUTCMonth() + 1)}${pad(d.getUTCDate())}`;
    const time = `${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}:${pad(d.getUTCSeconds())}.${pad(d.getUTCMilliseconds(), 3)}`;
    return `${date}-${time}`;
}

async function sendMessage() {
    const targetSession = document.getElementById('sendSessionSelect')?.value;
    if (!targetSession) return alert("Select a target session first.");

    const tagMap = {};
    document.querySelectorAll('#tagRows .tag-row').forEach(row => {
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

async function showFixMessageDetails(event, rawMessage, sessionString, validatedTags = []) {
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
            const isValidated = validatedTags && validatedTags.includes(parseInt(tag));
            const badge = isValidated ? ' <span class="badge bg-success ms-1" title="Validated by Test">&#10003;</span>' : '';
            const bgClass = isValidated ? 'table-success' : '';
            
            html += `
                <tr class="${bgClass}">
                    <td class="fw-bold ps-3">${tag}${badge}</td>
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
    const tbody = document.querySelector('#testExecutionTable tbody');
    const selectedFeatures = Array.from(document.querySelectorAll('.feature-checkbox:checked')).map(cb => cb.value);
    
    if (document.querySelectorAll('.feature-checkbox').length > 0 && selectedFeatures.length === 0) {
        return alert("Please select at least one feature file to run.");
    }

    const featureParam = selectedFeatures.length > 0 ? `?feature=${encodeURIComponent(selectedFeatures.join(','))}` : '';
    
    if (tbody) tbody.innerHTML = '';
    pre.textContent = "Running tests... Please wait.\n";
    
    try {
        const res = await fetch(`/api/tests/run${featureParam}`, { method: 'POST' });
        if (!res.ok) throw new Error("Request failed: " + res.statusText);
        
        const reader = res.body.getReader();
        const decoder = new TextDecoder('utf-8');
        pre.textContent = ""; // Clear waiting message once stream starts
        
        let buffer = "";
        let pendingMessages = [];

        while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            
            buffer += decoder.decode(value, { stream: true });
            let lines = buffer.split('\n');
            buffer = lines.pop(); // hold the incomplete line
            
            for (let line of lines) {
                if (line.startsWith('@@TEST_EVENT@@')) {
                    try {
                        const event = JSON.parse(line.substring(14));
                        handleTestEvent(event, tbody, pendingMessages);
                        if (event.event === 'TestStepFinished') pendingMessages = [];
                    } catch(e) { console.error("Parse event error", e); }
                } else if (line.startsWith('@@TEST_MSG@@')) {
                    try {
                        pendingMessages.push(JSON.parse(line.substring(12)));
                    } catch(e) { console.error("Parse msg error", e); }
                } else {
                    pre.textContent += line + "\n";
                    pre.scrollTop = pre.scrollHeight;
                }
            }
        }
        if (buffer && !buffer.startsWith('@@TEST_')) {
            pre.textContent += buffer;
            pre.scrollTop = pre.scrollHeight;
        }
    } catch (e) {
        pre.textContent += "\nError triggering tests: " + e;
    }
}

let currentScenarioRow = null;
let currentScenarioMessages = [];

function handleTestEvent(event, tbody, pendingMessages) {
    if (!tbody) return;
    if (event.event === 'TestCaseStarted') {
        const tr = document.createElement('tr');
        tr.className = 'table-secondary fw-bold';
        tr.innerHTML = `<td colspan="2">${event.name}</td><td class="scenario-msg-cell text-center"></td>`;
        tbody.appendChild(tr);
        currentScenarioRow = tr;
        currentScenarioMessages = [];
    } else if (event.event === 'TestStepFinished') {
        const tr = document.createElement('tr');
        
        let statusBadge = '';
        if (event.status === 'PASSED') statusBadge = '<span class="badge bg-success">PASS</span>';
        else if (event.status === 'FAILED') statusBadge = '<span class="badge bg-danger">FAIL</span>';
        else if (event.status === 'SKIPPED') statusBadge = '<span class="badge bg-warning text-dark">SKIP</span>';
        else statusBadge = `<span class="badge bg-secondary">${event.status}</span>`;

        let msgBtn = '';
        if (pendingMessages.length > 0) {
            currentScenarioMessages.push(...pendingMessages);
            const encoded = encodeURIComponent(JSON.stringify(pendingMessages));
            msgBtn = `<button class="btn btn-sm btn-outline-info p-0 px-1" onclick="showTestStepMessages(this)" data-msgs="${encoded}" title="View FIX Messages">&#128233;</button>`;
        }

        tr.innerHTML = `
            <td class="ps-4" style="word-break: break-word;">${event.name}</td>
            <td>${statusBadge}</td>
            <td class="text-center">${msgBtn}</td>
        `;
        tbody.appendChild(tr);

        if (event.status === 'FAILED' && event.error) {
            const trErr = document.createElement('tr');
            trErr.innerHTML = `<td colspan="3" class="text-danger small ps-4" style="white-space: pre-wrap;">${event.error}</td>`;
            tbody.appendChild(trErr);
        }
        
        const container = tbody.closest('.table-responsive');
        if (container) container.scrollTop = container.scrollHeight;
    } else if (event.event === 'TestCaseFinished') {
        if (currentScenarioRow && currentScenarioMessages.length > 0) {
            const encoded = encodeURIComponent(JSON.stringify(currentScenarioMessages));
            const cell = currentScenarioRow.querySelector('.scenario-msg-cell');
            if (cell) {
                cell.innerHTML = `<button class="btn btn-sm btn-primary p-0 px-2 fw-bold" onclick="showScenarioMessages(this)" data-msgs="${encoded}" title="Compare Sent vs Received Messages">&#8644;</button>`;
            }
        }
        currentScenarioRow = null;
        currentScenarioMessages = [];
    }
}

function showTestStepMessages(btn) {
    try {
        const msgs = JSON.parse(decodeURIComponent(btn.getAttribute('data-msgs')));
        if (msgs.length === 0) return;
        const lastMsg = msgs[msgs.length - 1]; // typically the matched/sent message
        showFixMessageDetails({ stopPropagation: () => {} }, lastMsg.message, lastMsg.session, lastMsg.validatedTags);
    } catch(e) {
        console.error("Could not show message", e);
    }
}

async function showScenarioMessages(btn) {
    const msgs = JSON.parse(decodeURIComponent(btn.getAttribute('data-msgs')));
    if (msgs.length === 0) return;

    let popup = document.getElementById('scenarioPopup');
    let backdrop = document.getElementById('scenarioBackdrop');

    if (!popup) {
        backdrop = document.createElement('div');
        backdrop.id = 'scenarioBackdrop';
        backdrop.style.position = 'fixed';
        backdrop.style.top = '0';
        backdrop.style.left = '0';
        backdrop.style.width = '100vw';
        backdrop.style.height = '100vh';
        backdrop.style.backgroundColor = 'rgba(0,0,0,0.5)';
        backdrop.style.zIndex = '1040';
        backdrop.onclick = () => { popup.style.display = 'none'; backdrop.style.display = 'none'; };
        document.body.appendChild(backdrop);

        popup = document.createElement('div');
        popup.id = 'scenarioPopup';
        popup.className = 'card shadow';
        popup.style.position = 'fixed';
        popup.style.top = '50%';
        popup.style.left = '50%';
        popup.style.transform = 'translate(-50%, -50%)';
        popup.style.backgroundColor = 'white';
        popup.style.zIndex = '1050';
        popup.style.maxHeight = '90vh';
        popup.style.width = '95vw';
        popup.style.maxWidth = '1400px';
        popup.style.display = 'flex';
        popup.style.flexDirection = 'column';

        popup.innerHTML = `
            <div class="card-header d-flex justify-content-between align-items-center bg-light">
                <h5 class="m-0">Scenario FIX Messages Comparison</h5>
                <button type="button" class="btn-close" onclick="document.getElementById('scenarioPopup').style.display='none'; document.getElementById('scenarioBackdrop').style.display='none';"></button>
            </div>
            <div class="card-body p-0 d-flex flex-column" style="overflow-y: auto; height: 100%;">
                <div id="scenarioComparisonContent" class="p-0 flex-grow-1"></div>
            </div>
        `;
        document.body.appendChild(popup);
    }

    popup.style.display = 'flex';
    backdrop.style.display = 'block';
    
    document.getElementById('scenarioComparisonContent').innerHTML = '<div class="p-3 text-muted">Loading...</div>';

    const outMsgs = msgs.filter(m => m.direction === 'OUT');
    const inMsgs = msgs.filter(m => m.direction === 'IN');

    document.getElementById('scenarioComparisonContent').innerHTML = await buildComparisonTables(outMsgs, inMsgs);
}

async function buildComparisonTables(outMsgs, inMsgs) {
    const count = Math.max(outMsgs.length, inMsgs.length);
    if (count === 0) return '<div class="p-4 text-muted text-center fw-bold">No messages</div>';
    
    let html = '';
    for (let i = 0; i < count; i++) {
        const outMsg = outMsgs[i];
        const inMsg = inMsgs[i];
        
        let localDictionary = fixDictionary;
        const sessionStr = (outMsg && outMsg.session) ? outMsg.session : (inMsg && inMsg.session ? inMsg.session : null);
        
        if (sessionStr) {
            const parts = sessionStr.split(':');
            if (parts.length > 0) {
                try {
                    const res = await fetch(`/api/dictionary?version=${encodeURIComponent(parts[0])}`);
                    localDictionary = await res.json();
                } catch (err) { }
            }
        }

        const outMap = {};
        const inMap = {};
        const allTagsSet = new Set();
        
        const inValidatedTags = (inMsg && inMsg.validatedTags) ? inMsg.validatedTags : [];
        
        function parseFields(msg, map) {
            if (!msg) return;
            msg.message.split('|').forEach(field => {
                if (!field) return;
                const parts = field.split('=');
                if (parts.length === 2) {
                    const tagNum = parseInt(parts[0], 10);
                    if (!isNaN(tagNum)) {
                        allTagsSet.add(tagNum);
                        if (map[parts[0]] !== undefined) map[parts[0]] += ", " + parts[1]; // Handle repeating groups cleanly
                        else map[parts[0]] = parts[1];
                    }
                }
            });
        }
        
        parseFields(outMsg, outMap);
        parseFields(inMsg, inMap);
        
        const allTags = Array.from(allTagsSet).sort((a, b) => a - b);

        html += `
            <div class="d-flex bg-light border-bottom border-top">
                <div class="w-50 p-2 border-end text-center text-primary fw-bold">Sent (OUT) ${outMsg ? '<br><small class="text-muted fw-normal">' + outMsg.session + '</small>' : ''}</div>
                <div class="w-50 p-2 text-center text-success fw-bold">Received (IN) ${inMsg ? '<br><small class="text-muted fw-normal">' + inMsg.session + '</small>' : ''}</div>
            </div>
            <table class="table table-sm table-bordered table-striped mb-0" style="table-layout: fixed;">
                <thead class="table-light">
                    <tr>
                        <th class="ps-3" style="width: 10%;">Tag</th>
                        <th style="width: 20%;">Description</th>
                        <th style="width: 35%;">Sent Value</th>
                        <th style="width: 35%;">Received Value</th>
                    </tr>
                </thead>
                <tbody>
        `;
        
        allTags.forEach(tagNum => {
            const tag = tagNum.toString();
            const outVal = outMap[tag] !== undefined ? outMap[tag] : '';
            const inVal = inMap[tag] !== undefined ? inMap[tag] : '';
            const desc = localDictionary[tag] || '';
            
            let highlightStyle = '';
            if (outVal !== '' && inVal !== '' && outVal !== inVal) {
                highlightStyle = 'background-color: #fff3cd;'; // light yellow highlight for mismatched tags
            }
            
            const isValidated = inValidatedTags.includes(tagNum);
            let badge = '';
            let inCellStyle = highlightStyle;
            if (isValidated) {
                badge = ' <span class="badge bg-success ms-1" title="Validated by Test">&#10003;</span>';
                inCellStyle = 'border: 2px solid #198754; background-color: #d1e7dd; font-weight: bold;';
            }
            
            html += `
                <tr>
                    <td class="fw-bold ps-3">${tag}${badge}</td>
                    <td class="text-muted text-truncate" title="${desc}">${desc}</td>
                    <td style="font-family: monospace; word-break: break-all; ${highlightStyle}">${outVal}</td>
                    <td style="font-family: monospace; word-break: break-all; ${inCellStyle}">${inVal}</td>
                </tr>
            `;
        });
        html += `</tbody></table>`;
    }
    return html;
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

async function loadAbout() {
    const container = document.getElementById('aboutContent');
    if (container.getAttribute('data-loaded') === 'true') return;
    
    try {
        const res = await fetch('readme.md');
        if (res.ok) {
            const text = await res.text();
            container.innerHTML = marked.parse(text);
            container.setAttribute('data-loaded', 'true');
        } else {
            container.innerHTML = '<span class="text-danger">Failed to load system documentation (readme.md).</span>';
        }
    } catch (e) {
        console.error("Failed to fetch readme.md", e);
        container.innerHTML = '<span class="text-danger">Error loading system information.</span>';
    }
}

function getCurrentMessageTags(containerId = 'tagRows') {
    const tagMap = {};
    document.querySelectorAll(`#${containerId} .tag-row`).forEach(row => {
        const tagInput = row.querySelector('.fix-tag');
        const valInput = row.querySelector('.fix-val');
        if (tagInput && valInput) {
            const tag = tagInput.value.trim();
            const val = valInput.value.trim();
            if (tag) tagMap[tag] = val;
        }
    });
    return tagMap;
}

function exportToCucumber(type = 'single') {
    let tags;
    try {
        tags = type === 'atdl' ? getAtdlMessageTags() : getCurrentMessageTags();
    } catch (e) {
        return alert(e.message);
    }
    if (Object.keys(tags).length === 0) return alert("No tags to export.");
    
    let str = "";
    for (const [tag, val] of Object.entries(tags)) {
        // Translate tag number to readable Dictionary name if possible
        const key = fixDictionary[tag] ? fixDictionary[tag] : tag;
        str += `      | ${key.padEnd(20)} | ${val} |\n`;
    }
    showExportPopup("Cucumber Feature Format", str.trimEnd());
}

function exportToMultiMessage(type = 'single') {
    let tags;
    try {
        tags = type === 'atdl' ? getAtdlMessageTags() : getCurrentMessageTags();
    } catch (e) {
        return alert(e.message);
    }
    if (Object.keys(tags).length === 0) return alert("No tags to export.");
    
    showExportPopup("Multiple Messages JSON Format", JSON.stringify([tags], null, 2));
}

function exportToTemplate(type = 'single') {
    let tags;
    try {
        tags = type === 'atdl' ? getAtdlMessageTags() : getCurrentMessageTags();
    } catch (e) {
        return alert(e.message);
    }
    if (Object.keys(tags).length === 0) return alert("No tags to export.");
    
    const template = {
        name: "Custom Exported Template",
        tags: tags
    };
    // Added a trailing comma so it's easy to paste straight into the message-templates.json array
    showExportPopup("Message Template JSON Format", JSON.stringify(template, null, 2) + ",");
}

function showExportPopup(title, content) {
    document.getElementById('exportTitle').innerText = title;
    document.getElementById('exportContent').innerText = content;
    document.getElementById('exportBackdrop').style.display = 'block';
    document.getElementById('exportPopup').style.display = 'block';
}

function closeExportPopup() {
    document.getElementById('exportBackdrop').style.display = 'none';
    document.getElementById('exportPopup').style.display = 'none';
}

async function copyExportContent() {
    const content = document.getElementById('exportContent').innerText;
    try {
        await navigator.clipboard.writeText(content);
    } catch (err) {
        alert("Failed to copy text. Please select and copy manually.");
    }
}

/* ================= ATDL SUPPORT ================= */

let currentAtdlDoc = null;

async function loadAtdlFiles() {
    try {
        const res = await fetch('/api/atdl/files');
        const files = await res.json();
        const sel = document.getElementById('atdlFileSelect');
        if (!sel) return;
        
        sel.innerHTML = '<option value="">-- Select ATDL File --</option>';
        files.forEach(f => sel.add(new Option(f, f)));
    } catch(e) { 
        console.error("Failed to load ATDL files", e); 
    }
}

async function loadAtdlFile() {
    const filename = document.getElementById('atdlFileSelect').value;
    const stratContainer = document.getElementById('atdlStrategyContainer');
    const formContainer = document.getElementById('atdlFormContainer');
    
    if (!filename) {
        stratContainer.style.display = 'none';
        formContainer.style.display = 'none';
        return;
    }
    
    try {
        const res = await fetch(`/api/atdl/file/${encodeURIComponent(filename)}`);
        const xmlText = await res.text();
        
        const parser = new DOMParser();
        currentAtdlDoc = parser.parseFromString(xmlText, "text/xml");

        const strategies = currentAtdlDoc.getElementsByTagNameNS("*", "Strategy");
        if (strategies.length === 0 && currentAtdlDoc.getElementsByTagName("Strategy").length > 0) {
            // Fallback for some browsers if namespace querying fails
            strategies = currentAtdlDoc.getElementsByTagName("Strategy");
        }

        const stratSel = document.getElementById('atdlStrategySelect');
        stratSel.innerHTML = '';
        for (let i = 0; i < strategies.length; i++) {
            const name = strategies[i].getAttribute('name');
            const uiRep = strategies[i].getAttribute('uiRep') || name;
            stratSel.add(new Option(uiRep, name));
        }
        
        stratContainer.style.display = 'block';
        renderAtdlForm();
    } catch (e) {
        alert("Error parsing ATDL XML: " + e.message);
    }
}

function renderAtdlForm() {
    const stratName = document.getElementById('atdlStrategySelect').value;
    const container = document.getElementById('atdlFormContainer');
    container.innerHTML = '';
    container.style.display = 'block';

    if (!currentAtdlDoc) return;

    const strategies = currentAtdlDoc.getElementsByTagNameNS("*", "Strategy");
    let stratNode = null;
    for (let i = 0; i < strategies.length; i++) {
        if (strategies[i].getAttribute('name') === stratName) {
            stratNode = strategies[i];
            break;
        }
    }
    if (!stratNode) return;

    const params = stratNode.getElementsByTagNameNS("*", "Parameter");
    let controlsRendered = 0;

    for (let i = 0; i < params.length; i++) {
        const p = params[i];
        const name = p.getAttribute('name');
        const fixTag = p.getAttribute('fixTag');
        const type = p.getAttribute('xsi:type') || '';
        const use = p.getAttribute('use');
        
        if (!fixTag) continue; // Only care about parameters mapped to a real FIX tag

        const enums = p.getElementsByTagNameNS("*", "EnumPair");
        const div = document.createElement('div');
        div.className = 'input-group mb-2 atdl-param-row';

        let inputHtml = '';
        if (enums.length > 0) {
            inputHtml = `<select class="form-select atdl-input" data-fixtag="${fixTag}">`;
            inputHtml += `<option value="">-- Select --</option>`;
            for (let j = 0; j < enums.length; j++) {
                const eVal = enums[j].getAttribute('wireValue');
                const eUi = enums[j].getAttribute('enumID') || eVal;
                inputHtml += `<option value="${eVal}">${eUi}</option>`;
            }
            inputHtml += `</select>`;
        } else if (type.includes('Boolean_t')) {
            inputHtml = `<select class="form-select atdl-input" data-fixtag="${fixTag}">
                <option value="">-- Select --</option>
                <option value="Y">Y - Yes</option>
                <option value="N">N - No</option>
            </select>`;
        } else {
            inputHtml = `<input type="text" class="form-control atdl-input" data-fixtag="${fixTag}" placeholder="${type}">`;
        }

        const reqHtml = use === 'required' ? ' <span class="text-danger" title="Required">*</span>' : '';

        div.innerHTML = `
            <span class="input-group-text" style="width: 220px; text-overflow: ellipsis; overflow: hidden; white-space: nowrap;" title="${name}">${name}${reqHtml} <small class="text-muted ms-1">(${fixTag})</small></span>
            ${inputHtml}
        `;
        container.appendChild(div);
        controlsRendered++;
    }
    
    if (controlsRendered === 0) {
        container.innerHTML = '<span class="text-muted">No mappable FIX Tag parameters found in this strategy.</span>';
    }
}

function getAtdlMessageTags() {
    const stratName = document.getElementById('atdlStrategySelect').value;
    const tagMap = {};
    
    // Get the selected strategy node from the ATDL document
    let stratNode = null;
    if (currentAtdlDoc) {
        const strategies = currentAtdlDoc.getElementsByTagNameNS("*", "Strategy");
        for (let i = 0; i < strategies.length; i++) {
            if (strategies[i].getAttribute('name') === stratName) {
                stratNode = strategies[i];
                break;
            }
        }
    }

    // Gather base order fields from dynamic rows
    document.querySelectorAll('#atdlTagRows .tag-row').forEach(row => {
        const tagInput = row.querySelector('.fix-tag');
        const valInput = row.querySelector('.fix-val');
        if (!tagInput || !valInput) return; // Skip if elements not found
        
        const tag = tagInput.value.trim();
        let val = valInput.value.trim();
        
        
        if (tag && val) tagMap[tag] = val;
    });

    // Gather ATDL strategy parameters, including hidden/constValue ones
    if (stratNode) {
        const params = stratNode.getElementsByTagNameNS("*", "Parameter");
        for (let i = 0; i < params.length; i++) {
            const p = params[i];
            const fixTag = p.getAttribute('fixTag');
            const constValue = p.getAttribute('constValue');
            
            // Add constValue parameters if not already set by a visible control
            if (fixTag && constValue !== null && constValue !== undefined && !tagMap[fixTag]) {
                tagMap[fixTag] = constValue;
            }
        }
    }

    // Add the main strategy identifier tag (e.g., 847=VWAP)
    if (stratNode && currentAtdlDoc) {
        let strategiesNode = currentAtdlDoc.getElementsByTagNameNS("*", "Strategies")[0];
        if (!strategiesNode) strategiesNode = currentAtdlDoc.getElementsByTagName("Strategies")[0];
        if (!strategiesNode) strategiesNode = currentAtdlDoc.documentElement; // Fallback to root

        const strategyTag = strategiesNode ? strategiesNode.getAttribute('strategyIdentifierTag') : null;
        const wireValue = stratNode.getAttribute('wireValue');
        
        if (strategyTag && wireValue) {
            tagMap[strategyTag] = wireValue;
        }
    }

    // Gather dynamic ATDL GUI form fields
    document.querySelectorAll('.atdl-input').forEach(input => {
        const tag = input.getAttribute('data-fixtag');
        const val = input.value.trim();
        if (tag && val !== '') {
            tagMap[tag] = val;
        }
    });

    // Validate required ATDL fields
    if (stratNode) {
        const params = stratNode.getElementsByTagNameNS("*", "Parameter");
        for (let i = 0; i < params.length; i++) {
            const p = params[i];
            const fixTag = p.getAttribute('fixTag');
            const name = p.getAttribute('name');
            const use = p.getAttribute('use');
            if (use === 'required' && fixTag && !tagMap[fixTag]) {
                throw new Error(`Mandatory ATDL parameter '${name}' (Tag ${fixTag}) is missing a value.`);
            }
        }
    }

    return tagMap;
}

async function sendAtdlOrder() {
    const targetSession = document.getElementById('atdlTargetSessionSelect').value;
    if (!targetSession) return alert("Select a target session first.");

    const stratName = document.getElementById('atdlStrategySelect').value;
    if (!stratName && currentAtdlDoc) return alert("Please select an ATDL strategy.");

    // Generate 11 and 60 if they exist in the form
    document.querySelectorAll('#atdlTagRows .tag-row').forEach(row => {
        const tagInput = row.querySelector('.fix-tag');
        const valInput = row.querySelector('.fix-val');
        if (!tagInput || !valInput) return;
        
        const tag = tagInput.value.trim();
        
        if (tag === "11") {
            valInput.value = "ATDL_" + Date.now();
        } else if (tag === "60") {
            valInput.value = generateFixTimestamp();
        }
    });

    let tagMap;
    try {
        tagMap = getAtdlMessageTags();
    } catch (e) {
        return alert(e.message);
    }

    const res = await fetch(`/api/sessions/${encodeURIComponent(targetSession)}/send`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(tagMap)
    });
    
    if (!res.ok) alert("Error sending ATDL message: " + await res.text());
}
