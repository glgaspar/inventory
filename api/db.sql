create table if not exists products (
    id bigserial primary key,
    barcode text not null unique check (barcode <> ''),
    name text not null check (name <> ''),
    category text not null check (category in ('kitchen', 'cleaning', 'bathroom', 'other')),
    unit text not null check (unit in ('un', 'kg', 'g', 'L', 'mL')),
    package_size numeric(12, 3) not null check (package_size > 0),
    image_url text,
    created_at timestamptz not null default now()
);

create table if not exists batches (
    id bigserial primary key,
    type text not null check (type in ('in', 'out')),
    at timestamptz not null,
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
