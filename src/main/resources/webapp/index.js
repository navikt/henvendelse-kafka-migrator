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
            check.append(el('pre', { className: 'description' }, description));
        }
        container.append(check);
    })
}

function autogrow(element) {
    element.style.height = 'auto';
    element.style.height = `${element.scrollHeight}px`;
}

async function createIntrospectionView() {
    const response = await fetch('/henvendelse-kafka-migrator/introspect');
    const tasks = await response.json();
    Object.values(tasks).forEach(renderIntrospectionTask);

    document.addEventListener('submit', async (e) => {
        e.preventDefault();
        if (e.target.className === 'introspection-task') {
            const taskname = e.target.dataset.name;
            const input = e.target.querySelector('.introspection-input');
            const output = e.target.querySelector('.introspection-output');
            const response = await fetch( `/henvendelse-kafka-migrator/introspect/${taskname}/run`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: input.value,
                credentials: 'include'
            });
            output.textContent = await response.text();
        }
    });
}

function renderIntrospectionTask(task) {
    const { name, description, inputExample } = task;
    console.log('task', task);
    const container = document.querySelector('.introspection-wrapper');

    const input = el('textarea', { className: 'introspection-input' });
    input.value = JSON.stringify(inputExample, null, 2);
    setTimeout(() => autogrow(input), 0);

    const output = el('pre', { className: 'introspection-output'});

    container.appendChild(el('section', {}, [
        el('h2', {}, name),
        el('p', {}, description),
        el('form', { className: 'introspection-task', 'data-name': name }, [
            input,
            el('button', {}, 'Execute'),
            output
        ])
    ]));
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
        el('button', { className: 'task-action', 'data-taskname': taskstatus.name, 'data-action': 'stop', [!taskstatus.isRunning ? 'disabled' : 'na']: true }, 'Stop'),
        el('button', { className: 'task-action', 'data-taskname': taskstatus.name, 'data-action': 'refresh' }, 'Refresh status')
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
            await fetch(`/henvendelse-kafka-migrator/task/${task}/start`, { method: 'POST', credentials: 'include' });
            const taskstatus = await fetch(`/henvendelse-kafka-migrator/task/${task}/status`).then(resp => resp.json());
            renderTask(taskstatus);
        },
        stop: async (task) => {
            await fetch(`/henvendelse-kafka-migrator/task/${task}/stop`, { method: 'POST', credentials: 'include' });
            const taskstatus = await fetch(`/henvendelse-kafka-migrator/task/${task}/status`).then(resp => resp.json());
            renderTask(taskstatus);
        },
        refresh: async (task) => {
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
    createIntrospectionView();
    addTaskActionListeners();
    createTasksView();
})();