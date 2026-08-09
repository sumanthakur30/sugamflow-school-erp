# Demo on-demand scripts (EC2)

Use these when you only want to burn RAM for the vertical you are showing.

| Script | When |
|--------|------|
| `up-retail.sh` | Everyday / GEN-MED-CLO-GRO shop demos |
| `up-clinic.sh` | Doctor / OPD / queue demo |
| `up-ipd.sh` | Hospital IPD + beds |
| `up-fieldforce.sh` | Fieldforce demo |
| `up-school.sh` | School ERP (`PHASE=all` default) |
| `down-demos.sh` | After demos — stop school/clinic/IPD/fieldforce |
| `status.sh` | See what is up |

## Paths on EC2

```bash
cd /opt/school/scripts/ec2/demo
chmod +x *.sh
```

Also mirrored at `/opt/sugamflow/scripts/demo` (same files).

## Typical day

```bash
# Morning — retail only
bash /opt/school/scripts/ec2/demo/up-retail.sh
bash /opt/school/scripts/ec2/demo/down-demos.sh   # if demos were left up overnight

# School pitch
bash /opt/school/scripts/ec2/demo/up-school.sh
# after: bash .../down-demos.sh

# Clinic pitch
bash /opt/school/scripts/ec2/demo/up-clinic.sh

# IPD pitch
bash /opt/school/scripts/ec2/demo/up-ipd.sh
```

## Always-on vs demo-only

**Keep up:** redis, config, discovery, auth, shop, user, notification, gateway, product, stock, order, payment, reporting, account, gst, ledger

**Start only for that demo:** school stack, doctor/appointment/queue, ipd/accommodation, fieldforce
