"""Independent Decimal oracle; no application code imports. Values are decimal strings.
TEM uses Decimal ln/exp; IRR solves the discount factor rather than monthly rate.
Conventions: full precision TEM; HALF_EVEN cents per period; final installment clears debt;
TOTAL grace capitalizes interest; balloon at maturity; fees payable also during grace.
"""
from decimal import Decimal as D, getcontext, ROUND_HALF_EVEN
import json
from pathlib import Path
getcontext().prec=300
CENT=D('.01'); ONE=D(1); ZERO=D(0)
def cents(x):return x.quantize(CENT,rounding=ROUND_HALF_EVEN)
def monthly(tea):return ((ONE+tea).ln()/D(12)).exp()-ONE
def reference(name,p,tea,n,property_value=None,life='0',insurance='0',admin='0',grace=0,kind='NONE',balloon='0',cok=None):
 p,tea,life,insurance,admin,balloon=map(D,[p,tea,life,insurance,admin,balloon]);value=D(property_value) if property_value else p
 rate=monthly(tea);balance=p;rows=[]
 for k in range(1,grace+1):
  interest=cents(balance*rate);principal=ZERO;payment=ZERO if kind=='TOTAL' else interest
  end=balance+interest if kind=='TOTAL' else balance
  rows.append(dict(k=k,initial=balance,interest=interest,principal=principal,payment=payment,life=cents(balance*life),insurance=cents(value*insurance),admin=cents(admin),final=end));balance=end
 m=n-grace;v=(ONE+rate)**(-m)
 exact=(balance-balloon*v)*rate/(ONE-v) if rate else (balance-balloon)/m
 regular=cents(exact)
 for k in range(grace+1,n+1):
  interest=cents(balance*rate);principal=balance if k==n else min(balance,max(ZERO,regular-interest))
  payment=principal+interest;end=cents(balance-principal)
  rows.append(dict(k=k,initial=balance,interest=interest,principal=principal,payment=payment,life=cents(balance*life),insurance=cents(value*insurance),admin=cents(admin),final=end));balance=end
 for row in rows:row['total']=row['payment']+row['life']+row['insurance']+row['admin']
 flows=[r['total'] for r in rows]
 if n==1:irr=flows[0]/p-ONE
 elif sum(flows)==p:irr=ZERO
 else:
  lo,hi=ZERO,ONE
  # sum(payment[t]*v**t) increases monotonically on [0,1]. Horner avoids repeated pow.
  for _ in range(700):
   mid=(lo+hi)/2;pv=ZERO
   for flow in reversed(flows):pv=(pv+flow)*mid
   if pv>p:hi=mid
   else:lo=mid
  irr=ONE/((lo+hi)/2)-ONE
 tcea=(ONE+irr)**12-ONE
 discount=tea if cok is None else D(cok);d=ONE/(ONE+monthly(discount));npv=p;factor=ONE
 for flow in flows:factor*=d;npv-=flow*factor
 sums={key:sum(r[key] for r in rows) for key in ['interest','principal','life','insurance','admin','total']}
 def sig(x,n=60):return format(x,f'.{n}g')
 return dict(name=name,input=dict(principal=str(p),tea=str(tea),months=n,propertyValue=str(value),lifeRate=str(life),propertyRate=str(insurance),monthlyFee=str(admin),graceMonths=grace,graceType=kind,balloon=str(balloon),cok=None if cok is None else str(discount)),tem=sig(rate),unroundedRegular=sig(exact),regular=str(regular),first={k:str(v) for k,v in rows[0].items()},firstRegular={k:str(v) for k,v in rows[grace].items()},last={k:str(v) for k,v in rows[-1].items()},sums={k:str(cents(v)) for k,v in sums.items()},monthlyIrr=sig(irr),tcea=sig(tcea),van=str(cents(npv)),rows=[{k:str(v) for k,v in row.items()}for row in rows])
cases=[
 reference('standard_no_fees','200000','.085',240),
 reference('standard_with_fees','200000','.085',240,'250000',life='.0005',insurance='.00025',admin='10'),
 reference('standard_with_fees_cok_zero','200000','.085',240,'250000',life='.0005',insurance='.00025',admin='10',cok='0'),
 reference('total_grace','150000','.09',120,life='.0005',insurance='.0002',grace=6,kind='TOTAL'),
 reference('partial_grace','100000','.08',60,life='.0004',insurance='.0002',grace=3,kind='PARTIAL'),
 reference('balloon_final','180000','.085',120,balloon='25000'),
 reference('maximum_loan_minimum_rate','9999999999999999.99','.00000001',360),
 reference('one_cent_minimum_rate','0.01','.00000001',360),
 reference('one_cent_extreme_fees_one_month','0.01','.00000001',1,'9999999999999999.99',life='1',insurance='1',admin='9999999999999999.99'),
]
path=Path(__file__).with_suffix('.json');path.write_text(json.dumps(cases,indent=2)+'\n')
for c in cases:print(json.dumps({k:v for k,v in c.items()if k not in ['rows','input']},ensure_ascii=False))
