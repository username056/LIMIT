output "ec2_entrypoint_records" {
  description = "EC2 Nginx로 연결된 DNS-only CNAME"
  value = {
    for subdomain, record in cloudflare_dns_record.ec2_entrypoint : subdomain => {
      id      = record.id
      name    = record.name
      content = record.content
      proxied = record.proxied
    }
  }
}
