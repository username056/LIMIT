#!/usr/bin/env bash
set -Eeuo pipefail

if [[ ${EUID:-$(id -u)} -ne 0 ]]; then
  echo "run as root: sudo bash scripts/provision-ec2-entrypoints.sh" >&2
  exit 77
fi

root_dir=$(cd -- "$(dirname -- "$0")/.." && pwd)
mode=${1:-full}
http_config="$root_dir/infra/nginx/limit-http.conf"
https_config="$root_dir/infra/nginx/limit.conf"
admin_dir="$root_dir/infra/admin"
grafana_assets_dir="$root_dir/infra/monitoring/grafana/assets"
nginx_config=/etc/nginx/conf.d/limit.conf
upstream_config=/etc/nginx/conf.d/limit-upstream.conf
cert_name=limit-admin
domains=(api.l1mit.shop admin.l1mit.shop docs.l1mit.shop grafana.l1mit.shop)

if [[ "$mode" != full && "$mode" != prepare ]]; then
  echo "usage: sudo bash scripts/provision-ec2-entrypoints.sh [prepare|full]" >&2
  exit 64
fi

for path in "$http_config" "$https_config" "$admin_dir/index.html" "$grafana_assets_dir"; do
  if [[ ! -e "$path" ]]; then
    echo "required file not found: $path" >&2
    exit 66
  fi
done

shopt -s nullglob
grafana_asset_files=("$grafana_assets_dir"/*.svg)
shopt -u nullglob
if (( ${#grafana_asset_files[@]} == 0 )); then
  echo "required Grafana SVG assets not found: $grafana_assets_dir" >&2
  exit 66
fi

export DEBIAN_FRONTEND=noninteractive
apt-get install -y nginx certbot

install -d -m 0755 /var/www/limit-certbot /var/www/limit-admin /var/www/limit-grafana-assets
install -m 0644 "$admin_dir/index.html" /var/www/limit-admin/index.html
install -m 0644 "$admin_dir/admin.css" /var/www/limit-admin/admin.css
install -m 0644 "$admin_dir/admin.js" /var/www/limit-admin/admin.js
for grafana_asset_file in "${grafana_asset_files[@]}"; do
  install -m 0644 "$grafana_asset_file" /var/www/limit-grafana-assets/
done
install -m 0644 "$http_config" "$nginx_config"

if [[ ! -f "$upstream_config" ]]; then
  printf 'upstream limit_backend {\n    server 127.0.0.1:8081;\n    keepalive 32;\n}\n' > "$upstream_config"
  chmod 0644 "$upstream_config"
fi

rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl enable --now nginx
systemctl reload nginx
ufw allow 80/tcp

if [[ "$mode" == prepare ]]; then
  echo "HTTP entrypoint is ready; run again with 'full' after DNS propagation."
  exit 0
fi

certbot_args=(
  certonly
  --webroot
  --webroot-path /var/www/limit-certbot
  --cert-name "$cert_name"
  --non-interactive
  --agree-tos
  --register-unsafely-without-email
  --keep-until-expiring
)
for domain in "${domains[@]}"; do
  certbot_args+=(-d "$domain")
done
certbot "${certbot_args[@]}"

install -m 0644 "$https_config" "$nginx_config"
nginx -t
systemctl reload nginx

echo "EC2 HTTPS entrypoints are configured."
