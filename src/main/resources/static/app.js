document.addEventListener('DOMContentLoaded', () => {
    loadSessions();
    addTagRow("35", "D"); // Add default MsgType=NewOrderSingle to the builder
});

let activeSessions = [];

async function loadSessions() {
    try {
        const res = await fetch('/api/sessions');
        activeSessions = await res.json();
        
        const tbody = document.querySelector('#sessionTable tbody');
        const selects = [document.getElementById('sendSessionSelect'), document.getElementById('replaySessionSelect')];
        
        tbody.innerHTML = '';
        selects.forEach(sel => sel.innerHTML = '');

        activeSessions.forEach(session => {
            // Populate Table
            const isLogged = session.isLoggedOn;
            const badge = isLogged ? '<span class="badge bg-success">Logged On</span>' : '<span class="badge bg-danger">Disconnected</span>';
            
            tbody.innerHTML += `
                <tr>
                    <td class="align-middle text-monospace">${session.sessionString}</td>
                    <td class="align-middle">${badge}</td>
                    <td>
                        <button class="btn btn-sm btn-outline-primary" onclick="sessionAction('${session.sessionString}', 'logon')">Logon</button>
                        <button class="btn btn-sm btn-outline-warning" onclick="sessionAction('${session.sessionString}', 'logout')">Logout</button>
                        <button class="btn btn-sm btn-outline-danger" onclick="sessionAction('${session.sessionString}', 'reset')">Reset Sequence</button>
                    </td>
                </tr>
            `;

            // Populate Dropdowns
            const opt = new Option(session.sessionString, session.sessionString);
            selects.forEach(sel => sel.add(opt.cloneNode(true)));
        });
    } catch (err) {
        console.error('Failed to load sessions', err);
    }
}

async function sessionAction(sessionString, action) {
    await fetch(`/api/sessions/${encodeURIComponent(sessionString)}/${action}`, { method: 'POST' });
    setTimeout(loadSessions, 500); // give the engine time to connect/disconnect before reloading UI
}

function addTagRow(defaultTag = "", defaultValue = "") {
    const row = document.createElement('div');
    row.className = 'input-group mb-2 w-50 tag-row';
    row.innerHTML = `
        <span class="input-group-text">Tag</span>
        <input type="text" class="form-control fix-tag" placeholder="e.g. 35" value="${defaultTag}">
        <span class="input-group-text">Value</span>
        <input type="text" class="form-control fix-val" placeholder="e.g. D" value="${defaultValue}">
        <button class="btn btn-outline-danger" onclick="this.parentElement.remove()">X</button>
    `;
    document.getElementById('tagRows').appendChild(row);
}

async function sendMessage() {
    const targetSession = document.getElementById('sendSessionSelect').value;
    if (!targetSession) return alert("Select a target session first.");

    const tagMap = {};
    document.querySelectorAll('.tag-row').forEach(row => {
        const tag = row.querySelector('.fix-tag').value.trim();
        const val = row.querySelector('.fix-val').value.trim();
        if (tag && val) tagMap[tag] = val;
    });

    const res = await fetch(`/api/sessions/${encodeURIComponent(targetSession)}/send`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(tagMap)
    });
    
    if (res.ok) alert("Message sent successfully.");
    else alert("Error sending message: " + await res.text());
}

async function replayMessages() {
    const targetSession = document.getElementById('replaySessionSelect').value;
    let payload;
    
    try {
        payload = JSON.parse(document.getElementById('replayJson').value);
    } catch (e) {
        return alert("Invalid JSON format in replay window.");
    }

    const res = await fetch(`/api/sessions/${encodeURIComponent(targetSession)}/replay`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    });
    
    if (res.ok) alert(await res.text());
    else alert("Replay failed: " + await res.text());
}

async function runTests() {
    const pre = document.getElementById('testOutput');
    pre.innerText = "Running tests... Please wait.";
    
    try {
        const res = await fetch(`/api/tests/run`, { method: 'POST' });
        const output = await res.text();
        pre.innerText = output;
    } catch (e) {
        pre.innerText = "Error triggering tests: " + e;
    }
}
