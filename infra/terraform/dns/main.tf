resource "cloudflare_dns_record" "ec2_entrypoint" {
  for_each = var.entrypoint_subdomains

  zone_id = var.cloudflare_zone_id
  name    = "${each.value}.${var.zone_name}"
  type    = "CNAME"
  content = var.ec2_origin_hostname
  ttl     = 1
  proxied = false
  comment = "Managed by Terraform: Limit ${each.value} entrypoint"
}
