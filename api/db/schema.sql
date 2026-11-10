create table if not exists users (
    id bigserial primary key,
    username text not null unique check (username ~ '^[a-z0-9._-]{1,50}$'),
    password_hash text not null,
    admin boolean not null default false,
    active boolean not null default true,
    created_at timestamptz not null default now()
);

create table if not exists tokens (
    hash text primary key,
    user_id bigint not null references users (id),
    created_at timestamptz not null default now(),
    last_used_at timestamptz not null default now()
);

create table if not exists products (
    id bigserial primary key,
    barcode text not null unique check (char_length(barcode) between 1 and 100),
    name text not null check (char_length(name) between 1 and 200),
    category text not null check (category in ('kitchen', 'cleaning', 'bathroom', 'other')),
    unit text not null check (unit in ('un', 'kg', 'g', 'L', 'mL')),
    package_size numeric(12, 3) not null check (package_size > 0),
    image_url text check (char_length(image_url) <= 500),
    created_at timestamptz not null default now()
);

create table if not exists batches (
    id bigserial primary key,
    type text not null check (type in ('in', 'out')),
    at timestamptz not null,
    user_id bigint not null references users (id),
    created_at timestamptz not null default now()
);

create table if not exists movements (
    id bigserial primary key,
    batch_id bigint not null references batches (id),
    product_id bigint not null references products (id),
    amount numeric(12, 3) not null check (amount > 0),
    packages int not null check (packages >= 0)
);

create index if not exists movements_product_id on movements (product_id);

create table if not exists activity (
    id bigserial primary key,
    user_id bigint references users (id),
    action text not null,
    details jsonb,
    ip text not null,
    at timestamptz not null default now()
);

create table if not exists lookups (
    barcode text primary key,
    result jsonb,
    fetched_at timestamptz not null default now()
);

create table if not exists images (
    id text primary key,
    content_type text not null,
    data bytea not null,
    created_at timestamptz not null default now()
);
