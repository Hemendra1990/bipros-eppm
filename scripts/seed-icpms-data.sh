#!/bin/bash

set -e

BASE_URL="http://localhost:8080"
ADMIN_USERNAME="admin"
ADMIN_PASSWORD="admin123"

echo "====== IC-PMS Demo Data Seeding Script ======"
echo "Base URL: $BASE_URL"
echo ""

# Step 1: Login and get token
echo "Step 1: Authenticating as admin..."
TOKEN=$(curl -s -X POST "$BASE_URL/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$ADMIN_USERNAME\",\"password\":\"$ADMIN_PASSWORD\"}" | \
  python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])")

if [ -z "$TOKEN" ]; then
  echo "ERROR: Failed to obtain authentication token"
  exit 1
fi
echo "✓ Authentication successful"
echo "Token: ${TOKEN:0:20}..."
echo ""

# Step 2: Get MRB-2026 project ID
echo "Step 2: Fetching MRB-2026 project ID..."
PROJ_ID=$(curl -s "$BASE_URL/v1/projects?size=50" \
  -H "Authorization: Bearer $TOKEN" | \
  python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    projects = d.get('data', {}).get('content', [])
    for p in projects:
        if p.get('code') == 'MRB-2026':
            print(p['id'])
            sys.exit(0)
    print('ERROR: MRB-2026 project not found', file=sys.stderr)
    sys.exit(1)
except Exception as e:
    print(f'ERROR: {e}', file=sys.stderr)
    sys.exit(1)
")

if [ -z "$PROJ_ID" ]; then
  echo "ERROR: Failed to get MRB-2026 project ID"
  exit 1
fi
echo "✓ Found MRB-2026 project ID: $PROJ_ID"
echo ""

# Step 3: Create Contracts
echo "Step 3: Creating contracts..."

# Contract 1: Main EPC Contract
echo "  Creating Contract 1 (Main EPC - MRB-EPC-001)..."
CONTRACT_1_RESPONSE=$(curl -s -X POST "$BASE_URL/v1/projects/$PROJ_ID/contracts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "projectId": "'$PROJ_ID'",
    "contractNumber": "MRB-EPC-001",
    "loaNumber": "LOA/MRB/2026/001",
    "contractorName": "Larsen & Toubro Infrastructure",
    "contractorCode": "LT-INFRA",
    "contractValue": 3200000000,
    "loaDate": "2026-05-15",
    "startDate": "2026-06-01",
    "completionDate": "2028-06-30",
    "dlpMonths": 24,
    "ldRate": 0.5,
    "contractType": "EPC"
  }')

CONTRACT_1_ID=$(echo "$CONTRACT_1_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('id',''))" 2>/dev/null || echo "")
if [ -z "$CONTRACT_1_ID" ]; then
  echo "  WARNING: Failed to create Contract 1. Response: $CONTRACT_1_RESPONSE"
else
  echo "  ✓ Contract 1 created: $CONTRACT_1_ID"
fi

# Contract 2: PMC Consultant
echo "  Creating Contract 2 (PMC Consultant - MRB-PMC-001)..."
CONTRACT_2_RESPONSE=$(curl -s -X POST "$BASE_URL/v1/projects/$PROJ_ID/contracts" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "projectId": "'$PROJ_ID'",
    "contractNumber": "MRB-PMC-001",
    "loaNumber": "LOA/MRB/2026/002",
    "contractorName": "RITES Limited",
    "contractorCode": "RITES",
    "contractValue": 150000000,
    "loaDate": "2026-04-20",
    "startDate": "2026-05-01",
    "completionDate": "2028-12-31",
    "dlpMonths": 12,
    "ldRate": 0.25,
    "contractType": "CONSULTANCY"
  }')

CONTRACT_2_ID=$(echo "$CONTRACT_2_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('id',''))" 2>/dev/null || echo "")
if [ -z "$CONTRACT_2_ID" ]; then
  echo "  WARNING: Failed to create Contract 2. Response: $CONTRACT_2_RESPONSE"
else
  echo "  ✓ Contract 2 created: $CONTRACT_2_ID"
fi
echo ""

# Step 4: Create Document Folders
echo "Step 4: Creating document folders..."

# DPR Documents folder
echo "  Creating DPR Documents folder..."
DPR_FOLDER=$(curl -s -X POST "$BASE_URL/v1/projects/$PROJ_ID/document-folders" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "name": "DPR Documents",
    "code": "DPR",
    "category": "DPR"
  }')

DPR_FOLDER_ID=$(echo "$DPR_FOLDER" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('id',''))" 2>/dev/null || echo "")
if [ -z "$DPR_FOLDER_ID" ]; then
  echo "  WARNING: Failed to create DPR folder. Response: $DPR_FOLDER"
else
  echo "  ✓ DPR Documents folder created: $DPR_FOLDER_ID"
fi

# Contract Documents folder
echo "  Creating Contract Documents folder..."
CONTRACT_FOLDER=$(curl -s -X POST "$BASE_URL/v1/projects/$PROJ_ID/document-folders" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "name": "Contract Documents",
    "code": "CONTRACT",
    "category": "CONTRACT"
  }')

CONTRACT_FOLDER_ID=$(echo "$CONTRACT_FOLDER" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('id',''))" 2>/dev/null || echo "")
if [ -z "$CONTRACT_FOLDER_ID" ]; then
  echo "  WARNING: Failed to create Contract Documents folder. Response: $CONTRACT_FOLDER"
else
  echo "  ✓ Contract Documents folder created: $CONTRACT_FOLDER_ID"
fi
echo ""

# Step 5: Create Drawings
echo "Step 5: Creating drawings..."

# Drawing 1: Foundation Plan - Pier 1
echo "  Creating Drawing 1 (Foundation Plan - Pier 1)..."
DRAWING_1=$(curl -s -X POST "$BASE_URL/v1/projects/$PROJ_ID/drawings" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "drawingNumber": "MRB-STR-001",
    "title": "Foundation Plan - Pier 1",
    "discipline": "STRUCTURAL",
    "revision": "R0",
    "revisionDate": "2026-05-20",
    "status": "IFC",
    "packageCode": "MRB.3",
    "scale": "1:100"
  }')

DRAWING_1_ID=$(echo "$DRAWING_1" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('id',''))" 2>/dev/null || echo "")
if [ -z "$DRAWING_1_ID" ]; then
  echo "  WARNING: Failed to create Drawing 1. Response: $DRAWING_1"
else
  echo "  ✓ Drawing 1 created: $DRAWING_1_ID"
fi

# Drawing 2: Road Alignment Plan
echo "  Creating Drawing 2 (Road Alignment Plan)..."
DRAWING_2=$(curl -s -X POST "$BASE_URL/v1/projects/$PROJ_ID/drawings" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "drawingNumber": "MRB-CIV-001",
    "title": "Road Alignment Plan",
    "discipline": "CIVIL",
    "revision": "R1",
    "revisionDate": "2026-06-10",
    "status": "IFA",
    "packageCode": "MRB.5",
    "scale": "1:500"
  }')

DRAWING_2_ID=$(echo "$DRAWING_2" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('id',''))" 2>/dev/null || echo "")
if [ -z "$DRAWING_2_ID" ]; then
  echo "  WARNING: Failed to create Drawing 2. Response: $DRAWING_2"
else
  echo "  ✓ Drawing 2 created: $DRAWING_2_ID"
fi
echo ""

# Step 6: Create RFIs
echo "Step 6: Creating RFIs (Requests for Information)..."

# RFI 1: Soil bearing capacity
echo "  Creating RFI 1 (Soil bearing capacity at Pier 2)..."
RFI_1=$(curl -s -X POST "$BASE_URL/v1/projects/$PROJ_ID/rfis" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "rfiNumber": "RFI-001",
    "subject": "Soil bearing capacity at Pier 2",
    "description": "Geotechnical report shows different values from DPR. Please clarify design basis.",
    "raisedBy": "Site Engineer",
    "assignedTo": "Structural Consultant",
    "raisedDate": "2026-07-15",
    "dueDate": "2026-07-22",
    "status": "OPEN",
    "priority": "HIGH"
  }')

RFI_1_ID=$(echo "$RFI_1" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('id',''))" 2>/dev/null || echo "")
if [ -z "$RFI_1_ID" ]; then
  echo "  WARNING: Failed to create RFI 1. Response: $RFI_1"
else
  echo "  ✓ RFI 1 created: $RFI_1_ID"
fi

# RFI 2: Steel grade for reinforcement
echo "  Creating RFI 2 (Steel grade for reinforcement)..."
RFI_2=$(curl -s -X POST "$BASE_URL/v1/projects/$PROJ_ID/rfis" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "rfiNumber": "RFI-002",
    "subject": "Steel grade for reinforcement",
    "description": "Is Fe 500D acceptable instead of Fe 550?",
    "raisedBy": "Contractor QC",
    "assignedTo": "PMC Structural",
    "raisedDate": "2026-07-20",
    "dueDate": "2026-07-30",
    "status": "RESPONDED",
    "priority": "MEDIUM",
    "response": "Fe 500D is acceptable as per design standards and specifications."
  }')

RFI_2_ID=$(echo "$RFI_2" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('id',''))" 2>/dev/null || echo "")
if [ -z "$RFI_2_ID" ]; then
  echo "  WARNING: Failed to create RFI 2. Response: $RFI_2"
else
  echo "  ✓ RFI 2 created: $RFI_2_ID"
fi
echo ""

# Step 7: Create Equipment Logs
echo "Step 7: Creating equipment logs..."

# First, fetch available equipment resources
echo "  Fetching available equipment resources..."
RESOURCES=$(curl -s "$BASE_URL/v1/resources?size=100" \
  -H "Authorization: Bearer $TOKEN" | \
  python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    resources = d.get('data', {}).get('content', [])
    equipment = [r for r in resources if r.get('type') == 'EQUIPMENT']
    for r in equipment[:3]:
        print(f'{r.get(\"id\")}:{r.get(\"code\")}')
except Exception as e:
    print(f'ERROR: {e}', file=sys.stderr)
" 2>/dev/null || echo "")

if [ -z "$RESOURCES" ]; then
  echo "  WARNING: No equipment resources found"
else
  # Parse first equipment resource
  EQUIPMENT_1=$(echo "$RESOURCES" | head -1 | cut -d: -f1)
  EQUIPMENT_1_CODE=$(echo "$RESOURCES" | head -1 | cut -d: -f2)
  EQUIPMENT_2=$(echo "$RESOURCES" | sed -n '2p' | cut -d: -f1)
  EQUIPMENT_2_CODE=$(echo "$RESOURCES" | sed -n '2p' | cut -d: -f2)
  EQUIPMENT_3=$(echo "$RESOURCES" | sed -n '3p' | cut -d: -f1)
  EQUIPMENT_3_CODE=$(echo "$RESOURCES" | sed -n '3p' | cut -d: -f2)

  if [ -n "$EQUIPMENT_1" ]; then
    echo "  Creating Equipment Log 1 (Tower Crane - $EQUIPMENT_1_CODE)..."
    EQUIP_LOG_1=$(curl -s -X POST "$BASE_URL/v1/projects/$PROJ_ID/equipment-logs" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer $TOKEN" \
      -d '{
        "resourceId": "'$EQUIPMENT_1'",
        "projectId": "'$PROJ_ID'",
        "logDate": "2026-07-25",
        "deploymentSite": "Pier 1 - Foundation Work",
        "operatingHours": 8.0,
        "idleHours": 1.0,
        "breakdownHours": 0.0,
        "fuelConsumed": 120.0,
        "operatorName": "Rajesh Kumar",
        "remarks": "Equipment working normally",
        "status": "WORKING"
      }')
    echo "  ✓ Equipment Log 1 created"
  fi

  if [ -n "$EQUIPMENT_2" ]; then
    echo "  Creating Equipment Log 2 (Excavator - $EQUIPMENT_2_CODE)..."
    EQUIP_LOG_2=$(curl -s -X POST "$BASE_URL/v1/projects/$PROJ_ID/equipment-logs" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer $TOKEN" \
      -d '{
        "resourceId": "'$EQUIPMENT_2'",
        "projectId": "'$PROJ_ID'",
        "logDate": "2026-07-25",
        "deploymentSite": "Quarry Site",
        "operatingHours": 10.0,
        "idleHours": 0.5,
        "breakdownHours": 1.5,
        "fuelConsumed": 80.0,
        "operatorName": "Vikram Singh",
        "remarks": "Minor pump issue - resolved",
        "status": "WORKING"
      }')
    echo "  ✓ Equipment Log 2 created"
  fi

  if [ -n "$EQUIPMENT_3" ]; then
    echo "  Creating Equipment Log 3 (Bulldozer - $EQUIPMENT_3_CODE)..."
    EQUIP_LOG_3=$(curl -s -X POST "$BASE_URL/v1/projects/$PROJ_ID/equipment-logs" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer $TOKEN" \
      -d '{
        "resourceId": "'$EQUIPMENT_3'",
        "projectId": "'$PROJ_ID'",
        "logDate": "2026-07-25",
        "deploymentSite": "Access Road - Earth Fill",
        "operatingHours": 6.0,
        "idleHours": 2.0,
        "breakdownHours": 0.0,
        "fuelConsumed": 95.0,
        "operatorName": "Arjun Patel",
        "remarks": "Regular maintenance completed",
        "status": "WORKING"
      }')
    echo "  ✓ Equipment Log 3 created"
  fi
fi
echo ""

# Step 8: Create Labour Returns
echo "Step 8: Creating labour returns..."

# Labour Return 1: SKILLED
echo "  Creating Labour Return 1 (SKILLED workers)..."
LABOUR_1=$(curl -s -X POST "$BASE_URL/v1/projects/$PROJ_ID/labour-returns" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "projectId": "'$PROJ_ID'",
    "contractorName": "L&T Infrastructure",
    "returnDate": "2026-07-25",
    "skillCategory": "SKILLED",
    "headCount": 25,
    "manDays": 25.0,
    "siteLocation": "Main Site",
    "remarks": "Steel fixers, shuttering workers"
  }')

LABOUR_1_ID=$(echo "$LABOUR_1" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('id',''))" 2>/dev/null || echo "")
if [ -z "$LABOUR_1_ID" ]; then
  echo "  WARNING: Failed to create Labour Return 1. Response: $LABOUR_1"
else
  echo "  ✓ Labour Return 1 created: $LABOUR_1_ID"
fi

# Labour Return 2: SEMI_SKILLED
echo "  Creating Labour Return 2 (SEMI-SKILLED workers)..."
LABOUR_2=$(curl -s -X POST "$BASE_URL/v1/projects/$PROJ_ID/labour-returns" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "projectId": "'$PROJ_ID'",
    "contractorName": "L&T Infrastructure",
    "returnDate": "2026-07-25",
    "skillCategory": "SEMI_SKILLED",
    "headCount": 45,
    "manDays": 45.0,
    "siteLocation": "Main Site",
    "remarks": "Welders, equipment operators"
  }')

LABOUR_2_ID=$(echo "$LABOUR_2" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('id',''))" 2>/dev/null || echo "")
if [ -z "$LABOUR_2_ID" ]; then
  echo "  WARNING: Failed to create Labour Return 2. Response: $LABOUR_2"
else
  echo "  ✓ Labour Return 2 created: $LABOUR_2_ID"
fi

# Labour Return 3: UNSKILLED
echo "  Creating Labour Return 3 (UNSKILLED workers)..."
LABOUR_3=$(curl -s -X POST "$BASE_URL/v1/projects/$PROJ_ID/labour-returns" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "projectId": "'$PROJ_ID'",
    "contractorName": "L&T Infrastructure",
    "returnDate": "2026-07-25",
    "skillCategory": "UNSKILLED",
    "headCount": 80,
    "manDays": 80.0,
    "siteLocation": "Main Site",
    "remarks": "Helpers, coolies, general labor"
  }')

LABOUR_3_ID=$(echo "$LABOUR_3" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('id',''))" 2>/dev/null || echo "")
if [ -z "$LABOUR_3_ID" ]; then
  echo "  WARNING: Failed to create Labour Return 3. Response: $LABOUR_3"
else
  echo "  ✓ Labour Return 3 created: $LABOUR_3_ID"
fi
echo ""

# Step 9: Create RA Bills
echo "Step 9: Creating RA (Running Account) Bills..."

# RA Bill 1: First bill for Contract 1
echo "  Creating RA Bill 1 (June 2026 - EPC Contract)..."
RA_BILL_1=$(curl -s -X POST "$BASE_URL/v1/projects/$PROJ_ID/ra-bills" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "projectId": "'$PROJ_ID'",
    "contractId": "'$CONTRACT_1_ID'",
    "billNumber": "RA-001",
    "billPeriodFrom": "2026-06-01",
    "billPeriodTo": "2026-06-30",
    "grossAmount": 45000000,
    "deductions": 2250000,
    "netAmount": 42750000,
    "remarks": "First running account bill for foundation work"
  }')

RA_BILL_1_ID=$(echo "$RA_BILL_1" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('id',''))" 2>/dev/null || echo "")
if [ -z "$RA_BILL_1_ID" ]; then
  echo "  WARNING: Failed to create RA Bill 1. Response: $RA_BILL_1"
else
  echo "  ✓ RA Bill 1 created: $RA_BILL_1_ID"
fi

# RA Bill 2: Second bill for Contract 1
echo "  Creating RA Bill 2 (July 2026 - EPC Contract)..."
RA_BILL_2=$(curl -s -X POST "$BASE_URL/v1/projects/$PROJ_ID/ra-bills" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "projectId": "'$PROJ_ID'",
    "contractId": "'$CONTRACT_1_ID'",
    "billNumber": "RA-002",
    "billPeriodFrom": "2026-07-01",
    "billPeriodTo": "2026-07-31",
    "grossAmount": 52000000,
    "deductions": 2600000,
    "netAmount": 49400000,
    "remarks": "Second running account bill for superstructure work"
  }')

RA_BILL_2_ID=$(echo "$RA_BILL_2" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('id',''))" 2>/dev/null || echo "")
if [ -z "$RA_BILL_2_ID" ]; then
  echo "  WARNING: Failed to create RA Bill 2. Response: $RA_BILL_2"
else
  echo "  ✓ RA Bill 2 created: $RA_BILL_2_ID"
fi

# RA Bill 3: First bill for Contract 2 (PMC)
echo "  Creating RA Bill 3 (May-June 2026 - PMC Contract)..."
RA_BILL_3=$(curl -s -X POST "$BASE_URL/v1/projects/$PROJ_ID/ra-bills" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "projectId": "'$PROJ_ID'",
    "contractId": "'$CONTRACT_2_ID'",
    "billNumber": "RA-PMC-001",
    "billPeriodFrom": "2026-05-01",
    "billPeriodTo": "2026-06-30",
    "grossAmount": 7500000,
    "deductions": 375000,
    "netAmount": 7125000,
    "remarks": "PMC services for 2 months - planning and supervision"
  }')

RA_BILL_3_ID=$(echo "$RA_BILL_3" | python3 -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('id',''))" 2>/dev/null || echo "")
if [ -z "$RA_BILL_3_ID" ]; then
  echo "  WARNING: Failed to create RA Bill 3. Response: $RA_BILL_3"
else
  echo "  ✓ RA Bill 3 created: $RA_BILL_3_ID"
fi
echo ""

echo "====== Data Seeding Complete ======"
echo ""
echo "Summary:"
echo "- Project ID: $PROJ_ID"
echo "- Contracts created: 2"
echo "  - Contract 1 (EPC): $CONTRACT_1_ID"
echo "  - Contract 2 (PMC): $CONTRACT_2_ID"
echo "- Document folders created: 2"
echo "- Drawings created: 2"
echo "- RFIs created: 2"
echo "- Equipment logs created: 3"
echo "- Labour returns created: 3"
echo "- RA Bills created: 3"
echo ""
echo "You can now navigate to the IC-PMS UI to see the seeded data."
