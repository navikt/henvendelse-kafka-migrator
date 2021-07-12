function el(type, props, content) {
    const element = document.createElement(type);
    if (content) {
        const arrContent = Array.isArray(content) ? content : [content];
        arrContent.forEach((c) => {
            if (typeof c === 'string') {
                element.appendChild(document.createTextNode(c));
            } else {
                element.appendChild(c);
            }
        })
    }
    Object.entries(props).forEach(([key, value]) => {
        if (key === 'className') {
            element.className = value;
        } else {
            element.setAttribute(key, value);
        }
    })
    return element;
}

async function createHealthchecksView() {
    const response = await fetch('/henvendelse-kafka-migrator/internal/healthchecks');
    const json = await response.json();
    const container = document.querySelector('.healthchecks');
    json.forEach(({ name, time, description, throwable }) => {
        const className = `healthcheck healthcheck--${throwable ? 'KO' : 'OK' }`;
        const check = el('section', { className });
        check.append(el('h1', {}, `${name} (${time}ms)`));
        if (throwable) {
            check.append(el('pre', { className: 'stacktrace' }, throwable));
        }
        if (description) {
            console.log('description', description);
            check.append(el('pre', { className: 'description' }, description));
        }
        container.append(check);
    })
}

async function delay(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms))
}

async function repeat(fn) {
    do {
        await fn();
        await delay(1000)
    } while (true)
}

async function createDebugView() {
    const container = document.querySelector('.debug-wrapper');
    container.appendChild(el('section', {}, [
        el('form', { className: 'processing' }, [
            el('label', {}, [
                'HenvendelseID',
                el('input', { type: 'text' })
            ]),
            el('button', {}, 'Søk')
        ])
    ]));
    container.appendChild(el('pre', { className: 'debug-output'}))
    document.addEventListener('submit', async (e) => {
        e.preventDefault();
        if (e.target.className === 'processing') {
            console.log('processing', e);
            const henvendelseId = e.target.querySelector('input').value;
            const response = await fetch('/henvendelse-kafka-migrator/internal/debug/processing', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ henvendelseId })
            });
            document.querySelector('.debug-output').textContent = await response.text();
        }
    });
}

async function createTasksView() {
    const response = await fetch('/henvendelse-kafka-migrator/task');
    const tasks = await response.json();
    Object.values(tasks).forEach(renderTask)
}

function renderTask(taskstatus) {
    const container = document.querySelector('.tasks');
    const task = el('section', { className: 'task', 'data-taskname': taskstatus.name });
    task.appendChild(el('h1', {}, taskstatus.name));
    task.appendChild(el('p', {}, taskstatus.description));

    const status = el('div', {}, [
        el('span', {}, [
            el('b', {}, 'Running: '),
            taskstatus.isRunning.toString()
        ]),
        el('span', {}, [
            el('b', {}, 'Done: '),
            taskstatus.isDone.toString()
        ]),
        el('span', {}, [
            el('b', {}, 'Processed: '),
            taskstatus.processed.toString()
        ])
    ]);
    const time = el('div', {}, [
        el('span', {}, [
            el('b', {}, 'Start: '),
            taskstatus.startingTime ?? 'N/A'
        ]),
        el('span', {}, [
            el('b', {}, 'End: '),
            taskstatus.endTime ?? 'N/A'
        ])
    ]);
    const buttons = el('div', {}, [
        el('button', { className: 'task-action', 'data-taskname': taskstatus.name, 'data-action': 'start', [taskstatus.isRunning ? 'disabled' : 'na']: true }, 'Start'),
        el('button', { className: 'task-action', 'data-taskname': taskstatus.name, 'data-action': 'stop', [!taskstatus.isRunning ? 'disabled' : 'na']: true }, 'Stop')
    ]);
    task.appendChild(status);
    task.appendChild(time);
    task.appendChild(buttons);

    const existing = container.querySelector(`.task[data-taskname=${taskstatus.name}]`)
    if (existing) {
        existing.replaceWith(task);
    } else {
        container.append(task);
    }
}

function addTaskActionListeners() {
    const taskmap = {
        start: async (task) => {
            await fetch(`/henvendelse-kafka-migrator/task/${task}/start`, { method: 'POST' });
            const taskstatus = await fetch(`/henvendelse-kafka-migrator/task/${task}/status`).then(resp => resp.json());
            renderTask(taskstatus);
        },
        stop: async (task) => {
            await fetch(`/henvendelse-kafka-migrator/task/${task}/stop`, { method: 'POST' });
            const taskstatus = await fetch(`/henvendelse-kafka-migrator/task/${task}/status`).then(resp => resp.json());
            renderTask(taskstatus);
        }
    }
    document.addEventListener('click', (e) => {
        if (e.target.className.includes('task-action')) {
            const taskname = e.target.dataset.taskname;
            const action = e.target.dataset.action;
            taskmap[action](taskname);
        }
    })
}

(function() {
    createHealthchecksView();
    createDebugView();
    addTaskActionListeners();
    repeat(createTasksView);
})();