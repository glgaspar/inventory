'use strict';

const strings = {
  en: {
    app: 'Inventory', inventory: 'Inventory', movement: 'Stock movement', users: 'Users', activity: 'Activity',
    password: 'Password', logout: 'Log out', login: 'Log in', username: 'Username', save: 'Save', cancel: 'Cancel',
    wrongLogin: 'Wrong username or password', sessionExpired: 'Your session expired, log in again',
    kitchen: 'Kitchen', cleaning: 'Cleaning', bathroom: 'Bathroom', other: 'Other', allCategories: 'All categories',
    search: 'Search', name: 'Name', category: 'Category', packages: 'Packages', amount: 'Amount', updated: 'Updated',
    inventoryEmpty: 'Nothing in stock yet', noMatch: 'Nothing matches', back: '← Inventory',
    barcode: 'Barcode', unit: 'Unit', packageSize: 'Amount per package', stock: 'In stock', history: 'History',
    historyEmpty: 'No movements yet', when: 'When', type: 'Type', user: 'User', in: 'Entry', out: 'Usage',
    scanHint: 'Scan or type a barcode and press Enter', sessionEmpty: 'Scan a product to add it',
    newProduct: 'New product', unknownBarcode: 'Barcode %1 is not registered yet', send: 'Send',
    searching: 'Searching product databases…', foundIn: 'Found in %1, check before saving', notFoundOnline: 'Not found online, fill it in',
    discard: 'Discard', discardConfirm: 'Discard the items in this movement?', sent: 'Movement sent',
    remove: 'Remove', fewer: 'Fewer packages', more: 'More packages',
    newUser: 'New user', admin: 'Admin', active: 'Active', created: 'Created', resetPassword: 'Reset password',
    newPasswordFor: 'New password for %1 (8 to 72 characters)', userCreated: 'User %1 created', saved: 'Saved',
    action: 'Action', details: 'Details', ip: 'IP', loadMore: 'Load more',
    currentPassword: 'Current password', newPassword: 'New password', confirmPassword: 'Repeat the new password',
    passwordMismatch: 'The new passwords are different', passwordChanged: 'Password changed. Other devices were logged out.',
    loadFailed: 'Could not load: %1',
    recipes: 'Recipes', recipeHint: 'Anything in mind? e.g. quick lunch, no oven', suggest: 'Suggest recipes', again: 'Suggest others',
    thinking: 'Thinking… this can take up to a minute', minutes: '%1 min', servings: '%1 servings', ingredients: 'Ingredients',
    steps: 'Steps', missingItems: 'To buy: %1', fromStock: 'in stock', recipesOff: 'Recipe suggestions are not configured on the server.',
    noKitchenStock: 'There is nothing from the kitchen in stock to cook with.', suggestedBy: 'Suggested by %1 · %2', cachedNote: ' (saved)',
  },
  pt: {
    app: 'Inventário', inventory: 'Estoque', movement: 'Movimentação', users: 'Usuários', activity: 'Atividade',
    password: 'Senha', logout: 'Sair', login: 'Entrar', username: 'Usuário', save: 'Salvar', cancel: 'Cancelar',
    wrongLogin: 'Usuário ou senha incorretos', sessionExpired: 'Sua sessão expirou, entre novamente',
    kitchen: 'Cozinha', cleaning: 'Limpeza', bathroom: 'Banheiro', other: 'Outros', allCategories: 'Todas as categorias',
    search: 'Buscar', name: 'Nome', category: 'Categoria', packages: 'Pacotes', amount: 'Total', updated: 'Atualizado',
    inventoryEmpty: 'Nada no estoque ainda', noMatch: 'Nada encontrado', back: '← Estoque',
    barcode: 'Código', unit: 'Unidade', packageSize: 'Quantidade por pacote', stock: 'Em estoque', history: 'Histórico',
    historyEmpty: 'Nenhuma movimentação ainda', when: 'Quando', type: 'Tipo', user: 'Usuário', in: 'Entrada', out: 'Consumo',
    scanHint: 'Leia ou digite um código e aperte Enter', sessionEmpty: 'Leia um produto para adicioná-lo',
    newProduct: 'Novo produto', unknownBarcode: 'O código %1 ainda não está cadastrado', send: 'Enviar',
    searching: 'Buscando nas bases de produtos…', foundIn: 'Encontrado em %1, confira antes de salvar', notFoundOnline: 'Não encontrado na internet, preencha os dados',
    discard: 'Descartar', discardConfirm: 'Descartar os itens desta movimentação?', sent: 'Movimentação enviada',
    remove: 'Remover', fewer: 'Menos pacotes', more: 'Mais pacotes',
    newUser: 'Novo usuário', admin: 'Admin', active: 'Ativo', created: 'Criado', resetPassword: 'Redefinir senha',
    newPasswordFor: 'Nova senha para %1 (8 a 72 caracteres)', userCreated: 'Usuário %1 criado', saved: 'Salvo',
    action: 'Ação', details: 'Detalhes', ip: 'IP', loadMore: 'Carregar mais',
    currentPassword: 'Senha atual', newPassword: 'Nova senha', confirmPassword: 'Repita a nova senha',
    passwordMismatch: 'As novas senhas são diferentes', passwordChanged: 'Senha alterada. Os outros aparelhos foram desconectados.',
    loadFailed: 'Não foi possível carregar: %1',
    recipes: 'Receitas', recipeHint: 'Alguma ideia? ex.: almoço rápido, sem forno', suggest: 'Sugerir receitas', again: 'Sugerir outras',
    thinking: 'Pensando… pode levar até um minuto', minutes: '%1 min', servings: '%1 porções', ingredients: 'Ingredientes',
    steps: 'Modo de preparo', missingItems: 'Falta comprar: %1', fromStock: 'no estoque', recipesOff: 'As sugestões de receitas não estão configuradas no servidor.',
    noKitchenStock: 'Não há nada da cozinha no estoque para cozinhar.', suggestedBy: 'Sugerido por %1 · %2', cachedNote: ' (salvo)',
  },
};

const lang = navigator.language.toLowerCase().startsWith('pt') ? 'pt' : 'en';
const locale = lang === 'pt' ? 'pt-BR' : 'en';
const categories = ['kitchen', 'cleaning', 'bathroom', 'other'];
const units = ['un', 'kg', 'g', 'L', 'mL'];
document.documentElement.lang = locale;
document.title = t('app');

function t(key, ...args) {
  return args.reduce((s, a, i) => s.replace('%' + (i + 1), a), strings[lang][key] ?? key);
}

const num = n => n.toLocaleString(locale, { maximumFractionDigits: 3 });
const amount = (n, unit) => num(n) + ' ' + unit;
const when = iso => new Date(iso).toLocaleString(locale, { dateStyle: 'short', timeStyle: 'short' });

function h(tag, attrs, ...children) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v == null || v === false) continue;
    if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
    else if (k === 'class') el.className = v;
    else if (k === 'value' || k === 'checked') el[k] = v;
    else el.setAttribute(k, v === true ? '' : v);
  }
  for (const c of children.flat()) {
    if (c != null && c !== false) el.append(c instanceof Node ? c : String(c));
  }
  return el;
}

function store(key, value) {
  try {
    if (value == null) localStorage.removeItem(key);
    else localStorage.setItem(key, JSON.stringify(value));
  } catch {}
}

function load(key) {
  try { return JSON.parse(localStorage.getItem(key)); } catch { return null; }
}

let toastTimer;
function toast(message, bad) {
  const el = document.getElementById('toast');
  el.textContent = message;
  el.className = bad ? 'bad' : '';
  el.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { el.hidden = true; }, 4000);
}

let token = load('token');
let me = null;

async function api(method, path, body) {
  const headers = {};
  if (token) headers.Authorization = 'Bearer ' + token;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const res = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  const data = res.status === 204 ? null : await res.json().catch(() => null);
  if (res.status === 401 && path !== '/login') {
    signOut();
    toast(t('sessionExpired'), true);
  }
  if (!res.ok) {
    const err = new Error((data && data.message) || res.statusText);
    err.status = res.status;
    throw err;
  }
  return data;
}

function signOut() {
  token = null;
  me = null;
  store('token', null);
  render();
}

const routes = {
  inventory: viewInventory,
  product: viewProduct,
  movement: viewMovement,
  recipes: viewRecipes,
  users: viewUsers,
  activity: viewActivity,
  password: viewPassword,
};
const adminRoutes = ['users', 'activity'];

async function render() {
  const app = document.getElementById('app');
  if (!token) {
    app.replaceChildren(viewLogin());
    return;
  }
  if (!me) {
    try {
      me = await api('GET', '/me');
    } catch (e) {
      if (e.status !== 401) app.replaceChildren(h('p', { class: 'content error' }, t('loadFailed', e.message)));
      return;
    }
  }
  let [name, arg] = location.hash.replace(/^#\/?/, '').split('/');
  if (!routes[name] || (adminRoutes.includes(name) && !me.admin)) name = 'inventory';
  const content = h('section', { class: 'content' });
  app.replaceChildren(nav(name), content);
  try {
    await routes[name](content, arg);
  } catch (e) {
    if (e.status !== 401) content.replaceChildren(h('p', { class: 'error' }, t('loadFailed', e.message)));
  }
}

function nav(current) {
  const link = (name, label) => h('a', { href: '#/' + name, class: current === name ? 'active' : null }, label);
  return h('nav', null,
    h('span', { class: 'brand' }, t('app')),
    link('inventory', t('inventory')),
    link('movement', t('movement')),
    link('recipes', t('recipes')),
    me.admin && link('users', t('users')),
    me.admin && link('activity', t('activity')),
    h('span', { class: 'spacer' }),
    h('span', { class: 'user hide-sm' }, me.username),
    link('password', t('password')),
    h('button', { class: 'link', onclick: logout }, t('logout')),
  );
}

async function logout() {
  try { await api('POST', '/logout'); } catch {}
  signOut();
}

function viewLogin() {
  const username = h('input', { autocomplete: 'username', required: true, autofocus: true });
  const password = h('input', { type: 'password', autocomplete: 'current-password', required: true });
  const error = h('p', { class: 'error', hidden: true });
  const button = h('button', null, t('login'));
  const form = h('form', {
    onsubmit: async e => {
      e.preventDefault();
      button.disabled = true;
      try {
        const res = await api('POST', '/login', { username: username.value, password: password.value });
        token = res.token;
        me = res.user;
        store('token', token);
        render();
      } catch (err) {
        error.textContent = err.status === 401 ? t('wrongLogin') : err.message;
        error.hidden = false;
        button.disabled = false;
      }
    },
  },
    h('label', null, t('username'), username),
    h('label', null, t('password'), password),
    error,
    button,
  );
  return h('div', { class: 'login card' }, h('h1', null, t('app')), form);
}

function table(headers, rows, emptyText) {
  if (!rows.length) return h('div', { class: 'panel empty' }, emptyText);
  return h('div', { class: 'panel scroll' }, h('table', null,
    h('thead', null, h('tr', null, headers.map(([label, cls]) => h('th', { class: cls }, label)))),
    h('tbody', null, rows),
  ));
}

async function viewInventory(el) {
  const items = await api('GET', '/inventory');
  const search = h('input', { class: 'grow', placeholder: t('search'), type: 'search' });
  const category = h('select', null,
    h('option', { value: '' }, t('allCategories')),
    categories.map(c => h('option', { value: c }, t(c))));
  const list = h('div');
  const draw = () => {
    const q = search.value.trim().toLowerCase();
    const shown = items.filter(i =>
      (!category.value || i.product.category === category.value) &&
      (!q || i.product.name.toLowerCase().includes(q) || i.product.barcode.includes(q)));
    const rows = shown.map(i => h('tr', { class: 'click', onclick: () => { location.hash = '#/product/' + i.product.id; } },
      h('td', null, h('a', { href: '#/product/' + i.product.id }, i.product.name)),
      h('td', { class: 'category hide-sm' }, t(i.product.category)),
      h('td', { class: 'num' }, num(i.packages)),
      h('td', { class: 'num' }, amount(i.amount, i.product.unit)),
      h('td', { class: 'num muted hide-sm' }, when(i.updatedAt)),
    ));
    list.replaceChildren(table(
      [[t('name'), 'wide'], [t('category'), 'hide-sm'], [t('packages'), 'num'], [t('amount'), 'num'], [t('updated'), 'num hide-sm']],
      rows, items.length ? t('noMatch') : t('inventoryEmpty')));
  };
  search.addEventListener('input', draw);
  category.addEventListener('change', draw);
  draw();
  el.replaceChildren(h('h1', null, t('inventory')), h('div', { class: 'row' }, search, category), list);
}

async function viewProduct(el, id) {
  const [product, history] = await Promise.all([api('GET', '/products/' + id), api('GET', '/products/' + id + '/movements')]);
  const stock = history.reduce((sum, m) => sum + (m.type === 'in' ? m.amount : -m.amount), 0);
  const rows = history.map(m => h('tr', null,
    h('td', null, when(m.at)),
    h('td', null, h('span', { class: 'pill ' + m.type }, t(m.type))),
    h('td', { class: 'num' }, num(m.packages)),
    h('td', { class: 'num' }, amount(m.amount, product.unit)),
    h('td', null, m.username),
  ));
  el.replaceChildren(
    h('p', null, h('a', { href: '#/inventory' }, t('back'))),
    h('div', { class: 'card product' },
      product.imageUrl && h('img', { src: product.imageUrl, alt: '' }),
      h('div', null,
        h('h1', null, product.name),
        h('dl', { class: 'facts' },
          h('dt', null, t('barcode')), h('dd', null, product.barcode),
          h('dt', null, t('category')), h('dd', null, t(product.category)),
          h('dt', null, t('packageSize')), h('dd', null, amount(product.packageSize, product.unit)),
          h('dt', null, t('stock')), h('dd', null, amount(stock, product.unit)),
        ),
      ),
    ),
    h('h2', null, t('history')),
    table([[t('when')], [t('type')], [t('packages'), 'num'], [t('amount'), 'num'], [t('user')]], rows, t('historyEmpty')),
  );
}

function viewMovement(el) {
  const session = load('session') || { type: 'in', items: [] };
  const save = () => store('session', session);
  const itemAmount = i => i.amount ?? i.packages * i.product.packageSize;

  const barcode = h('input', { class: 'grow', placeholder: t('scanHint'), autofocus: true });
  const typeToggle = h('div', { class: 'toggle' });
  const pending = h('div');
  const list = h('div');
  const sendButton = h('button', { onclick: send }, t('send'));
  const discardButton = h('button', { class: 'outline', onclick: discard }, t('discard'));
  let busy = false;

  function drawType() {
    typeToggle.replaceChildren(...['in', 'out'].map(type => h('button', {
      class: 'outline' + (session.type === type ? ' on' : ''),
      onclick: () => { session.type = type; save(); drawType(); },
    }, t(type))));
  }

  function add(product) {
    const existing = session.items.find(i => i.product.id === product.id);
    if (existing) {
      existing.packages++;
      existing.amount = null;
    } else {
      session.items.unshift({ product, packages: 1, amount: null });
    }
    save();
    drawList();
  }

  function drawList() {
    const rows = session.items.map(item => {
      const amountInput = h('input', {
        class: 'small', type: 'number', min: '0', step: 'any', value: itemAmount(item),
        onchange: () => { item.amount = Number(amountInput.value) || 0; save(); },
      });
      const setPackages = n => { item.packages = Math.max(1, n); item.amount = null; save(); drawList(); };
      return h('tr', null,
        h('td', null, item.product.name, h('div', { class: 'faint' }, item.product.barcode)),
        h('td', { class: 'num' },
          h('button', { class: 'outline icon', title: t('fewer'), onclick: () => setPackages(item.packages - 1) }, '−'),
          ' ', num(item.packages), ' ',
          h('button', { class: 'outline icon', title: t('more'), onclick: () => setPackages(item.packages + 1) }, '+')),
        h('td', { class: 'num' }, amountInput, ' ', item.product.unit),
        h('td', { class: 'num' }, h('button', {
          class: 'link', onclick: () => { session.items.splice(session.items.indexOf(item), 1); save(); drawList(); },
        }, t('remove'))),
      );
    });
    list.replaceChildren(table([[t('name'), 'wide'], [t('packages'), 'num'], [t('amount'), 'num'], ['', 'num']], rows, t('sessionEmpty')));
    sendButton.disabled = discardButton.disabled = !session.items.length;
  }

  async function lookup(code) {
    if (!code || busy) return;
    busy = true;
    try {
      add(await api('GET', '/products/by-barcode/' + encodeURIComponent(code)));
    } catch (e) {
      if (e.status === 404) pending.replaceChildren(newProductForm(code));
      else if (e.status !== 401) toast(e.message, true);
    } finally {
      busy = false;
    }
  }

  function newProductForm(code) {
    const name = h('input', { required: true, autofocus: true, maxlength: '200' });
    const category = h('select', null, categories.map(c => h('option', { value: c }, t(c))));
    const unit = h('select', null, units.map(u => h('option', { value: u }, u)));
    const size = h('input', { class: 'small', type: 'number', min: '0', step: 'any', required: true, value: '1' });
    const status = h('p', { class: 'faint' }, t('searching'));
    const preview = h('div');
    let imageUrl = null;
    const close = () => { pending.replaceChildren(); barcode.focus(); };
    const sourceNames = { openfoodfacts: 'Open Food Facts', openbeautyfacts: 'Open Beauty Facts', openproductsfacts: 'Open Products Facts' };
    api('GET', '/lookup/' + encodeURIComponent(code)).then(s => {
      if (!name.value) name.value = s.name;
      category.value = s.category;
      unit.value = s.unit;
      size.value = s.packageSize;
      imageUrl = s.imageUrl;
      if (imageUrl) preview.replaceChildren(h('img', { src: imageUrl, alt: '', class: 'thumb' }));
      status.textContent = t('foundIn', sourceNames[s.source] || s.source);
    }).catch(e => {
      status.textContent = e.status === 404 ? t('notFoundOnline') : e.message;
    });
    const form = h('form', {
      class: 'line',
      onsubmit: async e => {
        e.preventDefault();
        try {
          add(await api('POST', '/products', {
            barcode: code, name: name.value.trim(), category: category.value, unit: unit.value,
            packageSize: Number(size.value), imageUrl,
          }));
          close();
        } catch (err) {
          if (err.status !== 401) toast(err.message, true);
        }
      },
    },
      h('label', { class: 'grow' }, t('name'), name),
      h('label', null, t('category'), category),
      h('label', null, t('unit'), unit),
      h('label', null, t('packageSize'), size),
      h('button', null, t('save')),
      h('button', { type: 'button', class: 'outline', onclick: close }, t('cancel')),
    );
    setTimeout(() => name.focus());
    return h('div', { class: 'card' }, h('h3', null, t('newProduct')), h('p', { class: 'muted' }, t('unknownBarcode', code)), status, preview, form);
  }

  async function send() {
    sendButton.disabled = true;
    try {
      await api('POST', '/movements', {
        type: session.type,
        at: new Date().toISOString(),
        items: session.items.map(i => ({ productId: i.product.id, packages: i.packages, amount: itemAmount(i) })),
      });
      session.items = [];
      save();
      drawList();
      toast(t('sent'));
    } catch (e) {
      if (e.status !== 401) toast(e.message, true);
      sendButton.disabled = false;
    }
  }

  function discard() {
    if (!confirm(t('discardConfirm'))) return;
    session.items = [];
    save();
    drawList();
  }

  barcode.addEventListener('keydown', e => {
    if (e.key !== 'Enter') return;
    e.preventDefault();
    const code = barcode.value.trim();
    barcode.value = '';
    lookup(code);
  });

  drawType();
  drawList();
  el.replaceChildren(
    h('h1', null, t('movement')),
    h('div', { class: 'row' }, typeToggle, barcode),
    pending,
    list,
    h('div', { class: 'row end' }, discardButton, sendButton),
  );
  barcode.focus();
}

function viewRecipes(el) {
  const hint = h('input', { class: 'grow', maxlength: '200', placeholder: t('recipeHint'), value: load('recipeHint') || '' });
  const button = h('button', null, t('suggest'));
  const result = h('div');
  let shown = false;

  async function suggest(refresh) {
    store('recipeHint', hint.value);
    button.disabled = true;
    result.replaceChildren(h('p', { class: 'muted' }, t('thinking')));
    try {
      const r = await api('POST', '/recipes/suggest', { hint: hint.value, lang, refresh });
      result.replaceChildren(
        h('p', { class: 'faint' }, t('suggestedBy', r.model, when(r.generatedAt)) + (r.cached ? t('cachedNote') : '')),
        ...r.recipes.map(recipeCard));
      shown = true;
      button.textContent = t('again');
    } catch (e) {
      if (e.status === 401) return;
      const text = e.status === 503 ? t('recipesOff') : e.status === 409 ? t('noKitchenStock') : e.message;
      result.replaceChildren(h('p', { class: 'error' }, text));
    } finally {
      button.disabled = false;
    }
  }

  button.addEventListener('click', () => suggest(shown));
  hint.addEventListener('keydown', e => { if (e.key === 'Enter') suggest(false); });
  hint.addEventListener('input', () => { shown = false; button.textContent = t('suggest'); });
  el.replaceChildren(h('h1', null, t('recipes')), h('div', { class: 'row' }, hint, button), result);
}

function recipeCard(r) {
  return h('div', { class: 'card recipe' },
    h('h2', null, r.title),
    r.summary && h('p', { class: 'muted' }, r.summary),
    h('p', { class: 'faint' }, [r.minutes && t('minutes', r.minutes), r.servings && t('servings', r.servings)].filter(Boolean).join(' · ')),
    h('h3', null, t('ingredients')),
    h('ul', null, r.ingredients.map(i => h('li', null,
      [i.amount, i.name].filter(Boolean).join(' '),
      i.fromStock && h('span', { class: 'pill in' }, t('fromStock'))))),
    r.missing.length > 0 && h('p', { class: 'error' }, t('missingItems', r.missing.join(', '))),
    h('h3', null, t('steps')),
    h('ol', null, r.steps.map(s => h('li', null, s))),
  );
}

async function viewUsers(el) {
  const users = await api('GET', '/users');
  const update = async (user, change) => {
    try {
      await api('PATCH', '/users/' + user.id, change);
      toast(t('saved'));
    } catch (e) {
      if (e.status !== 401) toast(e.message, true);
    }
    render();
  };
  const rows = users.map(u => h('tr', null,
    h('td', null, u.username),
    h('td', null, h('input', { type: 'checkbox', checked: u.admin, disabled: u.id === me.id, onchange: e => update(u, { admin: e.target.checked }) })),
    h('td', null, h('input', { type: 'checkbox', checked: u.active, disabled: u.id === me.id, onchange: e => update(u, { active: e.target.checked }) })),
    h('td', { class: 'muted hide-sm' }, when(u.createdAt)),
    h('td', { class: 'num' }, h('button', {
      class: 'link',
      onclick: () => {
        const password = prompt(t('newPasswordFor', u.username));
        if (password) update(u, { password });
      },
    }, t('resetPassword'))),
  ));

  const username = h('input', { required: true, maxlength: '50', autocomplete: 'off' });
  const password = h('input', { type: 'password', required: true, minlength: '8', maxlength: '72', autocomplete: 'new-password' });
  const admin = h('input', { type: 'checkbox' });
  const form = h('form', {
    class: 'line',
    onsubmit: async e => {
      e.preventDefault();
      try {
        const created = await api('POST', '/users', { username: username.value, password: password.value, admin: admin.checked });
        toast(t('userCreated', created.username));
        render();
      } catch (err) {
        if (err.status !== 401) toast(err.message, true);
      }
    },
  },
    h('label', null, t('username'), username),
    h('label', null, t('password'), password),
    h('label', { class: 'inline' }, admin, t('admin')),
    h('button', null, t('save')),
  );

  el.replaceChildren(
    h('h1', null, t('users')),
    table([[t('username')], [t('admin')], [t('active')], [t('created'), 'hide-sm'], ['', 'num']], rows, ''),
    h('div', { class: 'card spaced' }, h('h2', null, t('newUser')), form),
  );
}

async function viewActivity(el) {
  const tbody = h('tbody');
  const more = h('button', { class: 'outline' }, t('loadMore'));
  let before = 0;
  const page = async () => {
    more.disabled = true;
    const entries = await api('GET', '/activity?limit=100' + (before ? '&before=' + before : ''));
    tbody.append(...entries.map(e => h('tr', null,
      h('td', { class: 'muted' }, when(e.at)),
      h('td', null, e.username ?? '—'),
      h('td', null, e.action),
      h('td', { class: 'details' }, e.details == null ? '' : JSON.stringify(e.details)),
      h('td', { class: 'muted hide-sm' }, e.ip),
    )));
    if (entries.length) before = entries[entries.length - 1].id;
    more.hidden = entries.length < 100;
    more.disabled = false;
  };
  more.addEventListener('click', () => page().catch(e => toast(e.message, true)));
  await page();
  el.replaceChildren(
    h('h1', null, t('activity')),
    h('div', { class: 'panel scroll' }, h('table', null,
      h('thead', null, h('tr', null,
        [t('when'), t('user'), t('action'), t('details')].map(l => h('th', null, l)), h('th', { class: 'hide-sm' }, t('ip')))),
      tbody)),
    h('div', { class: 'row end spaced' }, more),
  );
}

function viewPassword(el) {
  const current = h('input', { type: 'password', required: true, autocomplete: 'current-password' });
  const next = h('input', { type: 'password', required: true, minlength: '8', maxlength: '72', autocomplete: 'new-password' });
  const confirmation = h('input', { type: 'password', required: true, autocomplete: 'new-password' });
  const form = h('form', {
    class: 'stack',
    onsubmit: async e => {
      e.preventDefault();
      if (next.value !== confirmation.value) return toast(t('passwordMismatch'), true);
      try {
        await api('PUT', '/me/password', { current: current.value, new: next.value });
        form.reset();
        toast(t('passwordChanged'));
      } catch (err) {
        if (err.status !== 401) toast(err.message, true);
      }
    },
  },
    h('label', null, t('currentPassword'), current),
    h('label', null, t('newPassword'), next),
    h('label', null, t('confirmPassword'), confirmation),
    h('button', null, t('save')),
  );
  el.replaceChildren(h('div', { class: 'login card' }, h('h1', null, t('password')), form));
}

addEventListener('hashchange', render);
render();
