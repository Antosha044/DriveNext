create table if not exists public.users (
    id uuid primary key references auth.users(id) on delete cascade,
    email text not null unique,
    first_name text,
    last_name text,
    middle_name text,
    birth_date date,
    gender text check (gender is null or gender in ('Мужской', 'Женский')),
    license_number text unique check (license_number is null or length(license_number) = 10),
    license_issue_date date,
    profile_photo_path text,
    license_photo_path text,
    passport_photo_path text,
    created_at timestamptz not null default now()
);

alter table public.users enable row level security;

create policy "Users can read their own profile"
    on public.users for select to authenticated using (auth.uid() = id);
create policy "Users can create their own profile"
    on public.users for insert to authenticated with check (auth.uid() = id);
create policy "Users can update their own profile"
    on public.users for update to authenticated using (auth.uid() = id) with check (auth.uid() = id);

create or replace function public.create_drivenext_user_profile()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.users (
        id, email, first_name, last_name, middle_name, birth_date, gender,
        license_number, license_issue_date
    ) values (
        new.id,
        new.email,
        nullif(new.raw_user_meta_data->>'first_name', ''),
        nullif(new.raw_user_meta_data->>'last_name', ''),
        nullif(new.raw_user_meta_data->>'middle_name', ''),
        case when nullif(new.raw_user_meta_data->>'birth_date', '') is null then null
             else to_date(new.raw_user_meta_data->>'birth_date', 'DD/MM/YYYY') end,
        nullif(new.raw_user_meta_data->>'gender', ''),
        nullif(new.raw_user_meta_data->>'license_number', ''),
        case when nullif(new.raw_user_meta_data->>'license_issue_date', '') is null then null
             else to_date(new.raw_user_meta_data->>'license_issue_date', 'DD/MM/YYYY') end
    ) on conflict (id) do update set email = excluded.email;
    return new;
end;
$$;

drop trigger if exists on_auth_user_created_drivenext on auth.users;
create trigger on_auth_user_created_drivenext
    after insert on auth.users
    for each row execute procedure public.create_drivenext_user_profile();

insert into storage.buckets (id, name, public)
values ('documents', 'documents', false)
on conflict (id) do nothing;

create policy "Users can upload their own DriveNext documents"
    on storage.objects for insert to authenticated
    with check (bucket_id = 'documents' and (storage.foldername(name))[1] = auth.uid()::text);
create policy "Users can read their own DriveNext documents"
    on storage.objects for select to authenticated
    using (bucket_id = 'documents' and (storage.foldername(name))[1] = auth.uid()::text);
create policy "Users can replace their own DriveNext documents"
    on storage.objects for update to authenticated
    using (bucket_id = 'documents' and (storage.foldername(name))[1] = auth.uid()::text)
    with check (bucket_id = 'documents' and (storage.foldername(name))[1] = auth.uid()::text);
