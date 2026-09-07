/* GENERADO A PARTIR DE `infra/carga-de-datos/ejemplos/`, y no escrito a mano.
   ==========================================================================

   Las 23 filas de `fichas.csv`, las 4 de `sectores.csv`, las 13 de
   `manzanas.csv`, las 11 de `vias.csv` y las 51 de `detalle-de-fichas.csv`, con
   los nombres que publican los `record` del backend. Son los predios de
   DEMOSTRACION de Catacaos (ubigeo 200104) y **ninguno existe**: lo dice la
   cabecera de su propio CSV.

   Se derivan del archivo y no se transcriben porque el codigo de referencia
   catastral son 23 caracteres compuestos de diez tramos, y una errata ahi
   produce un codigo plausible que no casa con nada.

   Los conteos de sector y de manzana se CUENTAN sobre estas filas —no se
   copian—, que es lo mismo que hace el backend con su `SectorConConteos`: una
   cifra escrita a mano se separa de sus filas en cuanto alguien anade una. */
export const PADRON = [
  {
    "predioId": 1,
    "codRefCatastral": "20010401001001000000000",
    "tipo": "URBANO",
    "direccion": "Calle Comercio 245",
    "numeroMunicipal": "245",
    "codigoDeVia": "V-0003",
    "via": "Calle Comercio",
    "codigoDeSector": "01",
    "codigoDeManzana": "001",
    "lote": "001",
    "ubigeo": "200104",
    "estado": "ACTIVO",
    "fichado": true,
    "tipoFicha": "UNICA",
    "areaTerreno": "180.50",
    "uso": "Casa habitacion",
    "vigenciaDesde": "2026-01-01",
    "origen": "DECLARACION_JURADA",
    "documentoOrigen": "DJ-DEMO-0001",
    "contribuyente": "C-000001",
    "denominacion": null,
    "condicion": "PROPIETARIO_UNICO"
  },
  {
    "predioId": 2,
    "codRefCatastral": "20010401001002000000000",
    "tipo": "URBANO",
    "direccion": "Calle Comercio 251",
    "numeroMunicipal": "251",
    "codigoDeVia": "V-0003",
    "via": "Calle Comercio",
    "codigoDeSector": "01",
    "codigoDeManzana": "001",
    "lote": "002",
    "ubigeo": "200104",
    "estado": "ACTIVO",
    "fichado": true,
    "tipoFicha": "UNICA",
    "areaTerreno": "142.00",
    "uso": "Casa habitacion",
    "vigenciaDesde": "2026-01-01",
    "origen": "DECLARACION_JURADA",
    "documentoOrigen": "DJ-DEMO-0002",
    "contribuyente": "C-000002",
    "denominacion": null,
    "condicion": "PROPIETARIO_UNICO"
  },
  {
    "predioId": 3,
    "codRefCatastral": "20010401001003000000000",
    "tipo": "URBANO",
    "direccion": "Calle Comercio 263",
    "numeroMunicipal": "263",
    "codigoDeVia": "V-0003",
    "via": "Calle Comercio",
    "codigoDeSector": "01",
    "codigoDeManzana": "001",
    "lote": "003",
    "ubigeo": "200104",
    "estado": "ACTIVO",
    "fichado": true,
    "tipoFicha": "ECONOMICA",
    "areaTerreno": "265.75",
    "uso": "Tienda de artesania",
    "vigenciaDesde": "2026-01-01",
    "origen": "DECLARACION_JURADA",
    "documentoOrigen": "DJ-DEMO-0011",
    "contribuyente": "C-000006",
    "denominacion": "Sala de ventas Narihuala",
    "condicion": "PROPIETARIO_UNICO"
  },
  {
    "predioId": 4,
    "codRefCatastral": "20010401002001000000000",
    "tipo": "URBANO",
    "direccion": "Calle San Francisco 118",
    "numeroMunicipal": "118",
    "codigoDeVia": "V-0004",
    "via": "Calle San Francisco",
    "codigoDeSector": "01",
    "codigoDeManzana": "002",
    "lote": "001",
    "ubigeo": "200104",
    "estado": "ACTIVO",
    "fichado": true,
    "tipoFicha": "UNICA",
    "areaTerreno": "96.75",
    "uso": "Casa habitacion",
    "vigenciaDesde": "2026-01-01",
    "origen": "DECLARACION_JURADA",
    "documentoOrigen": "DJ-DEMO-0003",
    "contribuyente": "C-000003",
    "denominacion": null,
    "condicion": "PROPIETARIO_UNICO"
  },
  {
    "predioId": 5,
    "codRefCatastral": "20010401002002000000000",
    "tipo": "URBANO",
    "direccion": "Avenida Cayetano Heredia 402",
    "numeroMunicipal": "402",
    "codigoDeVia": "V-0001",
    "via": "Avenida Cayetano Heredia",
    "codigoDeSector": "01",
    "codigoDeManzana": "002",
    "lote": "002",
    "ubigeo": "200104",
    "estado": "ACTIVO",
    "fichado": true,
    "tipoFicha": "ECONOMICA",
    "areaTerreno": "320.00",
    "uso": "Taller de ceramica",
    "vigenciaDesde": "2026-01-01",
    "origen": "DECLARACION_JURADA",
    "documentoOrigen": "DJ-DEMO-0004",
    "contribuyente": "C-000006",
    "denominacion": "Taller Narihuala",
    "condicion": "PROPIETARIO_UNICO"
  },
  {
    "predioId": 6,
    "codRefCatastral": "20010401003001000000000",
    "tipo": "URBANO",
    "direccion": "Calle Ayacucho 512",
    "numeroMunicipal": "512",
    "codigoDeVia": "V-0007",
    "via": "Calle Ayacucho",
    "codigoDeSector": "01",
    "codigoDeManzana": "003",
    "lote": "001",
    "ubigeo": "200104",
    "estado": "ACTIVO",
    "fichado": true,
    "tipoFicha": "UNICA",
    "areaTerreno": "155.20",
    "uso": "Casa habitacion",
    "vigenciaDesde": "2026-01-01",
    "origen": "DECLARACION_JURADA",
    "documentoOrigen": "DJ-DEMO-0012",
    "contribuyente": "C-000013",
    "denominacion": null,
    "condicion": "PROPIETARIO_UNICO"
  },
  {
    "predioId": 7,
    "codRefCatastral": "20010401003002000000000",
    "tipo": "URBANO",
    "direccion": "Calle Ayacucho 528",
    "numeroMunicipal": "528",
    "codigoDeVia": "V-0007",
    "via": "Calle Ayacucho",
    "codigoDeSector": "01",
    "codigoDeManzana": "003",
    "lote": "002",
    "ubigeo": "200104",
    "estado": "ACTIVO",
    "fichado": true,
    "tipoFicha": "ECONOMICA",
    "areaTerreno": "210.00",
    "uso": "Panaderia y pasteleria",
    "vigenciaDesde": "2026-01-01",
    "origen": "DECLARACION_JURADA",
    "documentoOrigen": "DJ-DEMO-0013",
    "contribuyente": "C-000012",
    "denominacion": "Panaderia Simbila",
    "condicion": "PROPIETARIO_UNICO"
  },
  {
    "predioId": 8,
    "codRefCatastral": "20010401004001000000000",
    "tipo": "URBANO",
    "direccion": "Calle Grau 133",
    "numeroMunicipal": "133",
    "codigoDeVia": "V-0008",
    "via": "Calle Grau",
    "codigoDeSector": "01",
    "codigoDeManzana": "004",
    "lote": "001",
    "ubigeo": "200104",
    "estado": "ACTIVO",
    "fichado": true,
    "tipoFicha": "UNICA",
    "areaTerreno": "240.80",
    "uso": "Casa habitacion",
    "vigenciaDesde": "2026-01-01",
    "origen": "DECLARACION_JURADA",
    "documentoOrigen": "DJ-DEMO-0014",
    "contribuyente": "C-000014",
    "denominacion": null,
    "condicion": "PROPIETARIO_UNICO"
  },
  {
    "predioId": 9,
    "codRefCatastral": "20010401004002000000000",
    "tipo": "URBANO",
    "direccion": "Calle Grau 149",
    "numeroMunicipal": "149",
    "codigoDeVia": "V-0008",
    "via": "Calle Grau",
    "codigoDeSector": "01",
    "codigoDeManzana": "004",
    "lote": "002",
    "ubigeo": "200104",
    "estado": "ACTIVO",
    "fichado": true,
    "tipoFicha": "UNICA",
    "areaTerreno": "118.30",
    "uso": "Casa habitacion",
    "vigenciaDesde": "2026-01-01",
    "origen": "DECLARACION_JURADA",
    "documentoOrigen": "DJ-DEMO-0015",
    "contribuyente": "C-000013",
    "denominacion": null,
    "condicion": "PROPIETARIO_UNICO"
  },
  {
    "predioId": 10,
    "codRefCatastral": "20010401005001000000000",
    "tipo": "URBANO",
    "direccion": "Calle Bolognesi 204",
    "numeroMunicipal": "204",
    "codigoDeVia": "V-0009",
    "via": "Calle Bolognesi",
    "codigoDeSector": "01",
    "codigoDeManzana": "005",
    "lote": "001",
    "ubigeo": "200104",
    "estado": "ACTIVO",
    "fichado": true,
    "tipoFicha": "UNICA",
    "areaTerreno": "305.40",
    "uso": "Casa habitacion",
    "vigenciaDesde": "2026-01-01",
    "origen": "DECLARACION_JURADA",
    "documentoOrigen": "DJ-DEMO-0016",
    "contribuyente": "C-000014",
    "denominacion": null,
    "condicion": "PROPIETARIO_UNICO"
  },
  {
    "predioId": 11,
    "codRefCatastral": "20010401005002000000000",
    "tipo": "URBANO",
    "direccion": "Calle Bolognesi 218",
    "numeroMunicipal": "218",
    "codigoDeVia": "V-0009",
    "via": "Calle Bolognesi",
    "codigoDeSector": "01",
    "codigoDeManzana": "005",
    "lote": "002",
    "ubigeo": "200104",
    "estado": "ACTIVO",
    "fichado": true,
    "tipoFicha": "UNICA",
    "areaTerreno": "132.60",
    "uso": "Casa habitacion",
    "vigenciaDesde": "2026-01-01",
    "origen": "DECLARACION_JURADA",
    "documentoOrigen": "DJ-DEMO-0017",
    "contribuyente": "C-000004",
    "denominacion": null,
    "condicion": "PROPIETARIO_UNICO"
  },
  {
    "predioId": 12,
    "codRefCatastral": "20010402001001000000000",
    "tipo": "URBANO",
    "direccion": "Calle Junin 77",
    "numeroMunicipal": "77",
    "codigoDeVia": "V-0005",
    "via": "Calle Junin",
    "codigoDeSector": "02",
    "codigoDeManzana": "001",
    "lote": "001",
    "ubigeo": "200104",
    "estado": "ACTIVO",
    "fichado": true,
    "tipoFicha": "UNICA",
    "areaTerreno": "210.30",
    "uso": "Casa habitacion",
    "vigenciaDesde": "2026-01-01",
    "origen": "FISCALIZACION",
    "documentoOrigen": "ACTA-DEMO-0005",
    "contribuyente": "C-000004",
    "denominacion": null,
    "condicion": "COPROPIETARIO"
  },
  {
    "predioId": 13,
    "codRefCatastral": "20010402002001000000000",
    "tipo": "URBANO",
    "direccion": "Calle Piura 310",
    "numeroMunicipal": "310",
    "codigoDeVia": "V-0006",
    "via": "Calle Piura",
    "codigoDeSector": "02",
    "codigoDeManzana": "002",
    "lote": "001",
    "ubigeo": "200104",
    "estado": "ACTIVO",
    "fichado": true,
    "tipoFicha": "UNICA",
    "areaTerreno": "88.40",
    "uso": "Casa habitacion",
    "vigenciaDesde": "2026-01-01",
    "origen": "FISCALIZACION",
    "documentoOrigen": "ACTA-DEMO-0006",
    "contribuyente": "C-000005",
    "denominacion": null,
    "condicion": "POSEEDOR"
  },
  {
    "predioId": 14,
    "codRefCatastral": "20010402003001010103201",
    "tipo": "URBANO",
    "direccion": "Pasaje Los Ceramistas 15 Dpto 201",
    "numeroMunicipal": "15",
    "codigoDeVia": "V-0013",
    "via": "Pasaje Los Ceramistas",
    "codigoDeSector": "02",
    "codigoDeManzana": "003",
    "lote": "001",
    "ubigeo": "200104",
    "estado": "ACTIVO",
    "fichado": true,
    "tipoFicha": "BIENES_COMUNES",
    "areaTerreno": "64.20",
    "uso": "Departamento",
    "vigenciaDesde": "2026-01-01",
    "origen": "DECLARACION_JURADA",
    "documentoOrigen": "DJ-DEMO-0007",
    "contribuyente": "C-000007",
    "denominacion": "Edificio Los Ceramistas",
    "condicion": "PROPIETARIO_UNICO"
  },
  {
    "predioId": 15,
    "codRefCatastral": "20010402003002010104401",
    "tipo": "URBANO",
    "direccion": "Pasaje Los Ceramistas 15 Dpto 401",
    "numeroMunicipal": "15",
    "codigoDeVia": "V-0013",
    "via": "Pasaje Los Ceramistas",
    "codigoDeSector": "02",
    "codigoDeManzana": "003",
    "lote": "002",
    "ubigeo": "200104",
    "estado": "ACTIVO",
    "fichado": true,
    "tipoFicha": "BIENES_COMUNES",
    "areaTerreno": "64.20",
    "uso": "Departamento",
    "vigenciaDesde": "2026-01-01",
    "origen": "DECLARACION_JURADA",
    "documentoOrigen": "DJ-DEMO-0018",
    "contribuyente": "C-000007",
    "denominacion": "Edificio Los Ceramistas",
    "condicion": "PROPIETARIO_UNICO"
  },
  {
    "predioId": 16,
    "codRefCatastral": "20010402004001000000000",
    "tipo": "URBANO",
    "direccion": "Calle Tacna 66",
    "numeroMunicipal": "66",
    "codigoDeVia": "V-0010",
    "via": "Calle Tacna",
    "codigoDeSector": "02",
    "codigoDeManzana": "004",
    "lote": "001",
    "ubigeo": "200104",
    "estado": "ACTIVO",
    "fichado": true,
    "tipoFicha": "ECONOMICA",
    "areaTerreno": "412.90",
    "uso": "Almacen de insumos",
    "vigenciaDesde": "2026-01-01",
    "origen": "DECLARACION_JURADA",
    "documentoOrigen": "DJ-DEMO-0019",
    "contribuyente": "C-000014",
    "denominacion": "Almacen Querevalu",
    "condicion": "PROPIETARIO_UNICO"
  },
  {
    "predioId": 17,
    "codRefCatastral": "20010402004002000000000",
    "tipo": "URBANO",
    "direccion": "Calle Tacna 82",
    "numeroMunicipal": "82",
    "codigoDeVia": "V-0010",
    "via": "Calle Tacna",
    "codigoDeSector": "02",
    "codigoDeManzana": "004",
    "lote": "002",
    "ubigeo": "200104",
    "estado": "ACTIVO",
    "fichado": true,
    "tipoFicha": "UNICA",
    "areaTerreno": "124.00",
    "uso": "Casa habitacion",
    "vigenciaDesde": "2026-01-01",
    "origen": "FISCALIZACION",
    "documentoOrigen": "ACTA-DEMO-0020",
    "contribuyente": "",
    "denominacion": null,
    "condicion": ""
  },
  {
    "predioId": 18,
    "codRefCatastral": "20010403001001000000000",
    "tipo": "URBANO",
    "direccion": "Avenida Progreso 890",
    "numeroMunicipal": "890",
    "codigoDeVia": "V-0002",
    "via": "Avenida Progreso",
    "codigoDeSector": "03",
    "codigoDeManzana": "001",
    "lote": "001",
    "ubigeo": "200104",
    "estado": "ACTIVO",
    "fichado": true,
    "tipoFicha": "ECONOMICA",
    "areaTerreno": "455.00",
    "uso": "Deposito y patio de maniobras",
    "vigenciaDesde": "2026-01-01",
    "origen": "DECLARACION_JURADA",
    "documentoOrigen": "DJ-DEMO-0008",
    "contribuyente": "C-000007",
    "denominacion": "Patio La Legua",
    "condicion": "PROPIETARIO_UNICO"
  },
  {
    "predioId": 19,
    "codRefCatastral": "20010403002001000000000",
    "tipo": "URBANO",
    "direccion": "Jiron Lima 45",
    "numeroMunicipal": "45",
    "codigoDeVia": "V-0011",
    "via": "Jiron Lima",
    "codigoDeSector": "03",
    "codigoDeManzana": "002",
    "lote": "001",
    "ubigeo": "200104",
    "estado": "ACTIVO",
    "fichado": true,
    "tipoFicha": "UNICA",
    "areaTerreno": "176.10",
    "uso": "Casa habitacion",
    "vigenciaDesde": "2026-01-01",
    "origen": "DECLARACION_JURADA",
    "documentoOrigen": "DJ-DEMO-0021",
    "contribuyente": "C-000014",
    "denominacion": null,
    "condicion": "PROPIETARIO_UNICO"
  },
  {
    "predioId": 20,
    "codRefCatastral": "20010403003001000000000",
    "tipo": "URBANO",
    "direccion": "Jiron Cusco 900",
    "numeroMunicipal": "900",
    "codigoDeVia": "V-0012",
    "via": "Jiron Cusco",
    "codigoDeSector": "03",
    "codigoDeManzana": "003",
    "lote": "001",
    "ubigeo": "200104",
    "estado": "ACTIVO",
    "fichado": true,
    "tipoFicha": "UNICA",
    "areaTerreno": "620.00",
    "uso": "Terreno sin construir",
    "vigenciaDesde": "2026-01-01",
    "origen": "DECLARACION_JURADA",
    "documentoOrigen": "DJ-DEMO-0022",
    "contribuyente": "C-000015",
    "denominacion": null,
    "condicion": "PROPIETARIO_UNICO"
  },
  {
    "predioId": 21,
    "codRefCatastral": "20010404001001000000000",
    "tipo": "RUSTICO",
    "direccion": "Carretera Catacaos - La Legua km 4",
    "numeroMunicipal": null,
    "codigoDeVia": "V-0015",
    "via": "Carretera Catacaos - La Legua",
    "codigoDeSector": "04",
    "codigoDeManzana": "001",
    "lote": "001",
    "ubigeo": "200104",
    "estado": "ACTIVO",
    "fichado": true,
    "tipoFicha": "RURAL",
    "areaTerreno": "12500.00",
    "uso": "Cultivo bajo riego",
    "vigenciaDesde": "2026-01-01",
    "origen": "MIGRACION",
    "documentoOrigen": "MIG-DEMO-0009",
    "contribuyente": "C-000008",
    "denominacion": "Fundo Simbila Chico",
    "condicion": "SUCESION"
  },
  {
    "predioId": 22,
    "codRefCatastral": "20010404002001000000000",
    "tipo": "RUSTICO",
    "direccion": "Carretera Catacaos - La Legua km 6",
    "numeroMunicipal": null,
    "codigoDeVia": "V-0015",
    "via": "Carretera Catacaos - La Legua",
    "codigoDeSector": "04",
    "codigoDeManzana": "002",
    "lote": "001",
    "ubigeo": "200104",
    "estado": "ACTIVO",
    "fichado": true,
    "tipoFicha": "RURAL",
    "areaTerreno": "8300.00",
    "uso": "Cultivo bajo riego",
    "vigenciaDesde": "2026-01-01",
    "origen": "MIGRACION",
    "documentoOrigen": "MIG-DEMO-0010",
    "contribuyente": "C-000001",
    "denominacion": "Fundo Simbila Grande",
    "condicion": "CONYUGE"
  },
  {
    "predioId": 23,
    "codRefCatastral": "20010404003001000000000",
    "tipo": "RUSTICO",
    "direccion": "Carretera Catacaos - La Legua km 9",
    "numeroMunicipal": null,
    "codigoDeVia": "V-0015",
    "via": "Carretera Catacaos - La Legua",
    "codigoDeSector": "04",
    "codigoDeManzana": "003",
    "lote": "001",
    "ubigeo": "200104",
    "estado": "ACTIVO",
    "fichado": true,
    "tipoFicha": "RURAL",
    "areaTerreno": "21400.00",
    "uso": "Cultivo bajo riego",
    "vigenciaDesde": "2026-01-01",
    "origen": "MIGRACION",
    "documentoOrigen": "MIG-DEMO-0023",
    "contribuyente": "C-000015",
    "denominacion": "Fundo Tallan",
    "condicion": "PROPIETARIO_UNICO"
  }
];
export const SECTORES = [
  {
    "id": 1,
    "codigo": "01",
    "nombre": "Cercado de Catacaos",
    "zona": "Urbana",
    "activo": true,
    "manzanas": 5,
    "predios": 11,
    "lotes": 11
  },
  {
    "id": 2,
    "codigo": "02",
    "nombre": "Narihuala",
    "zona": "Urbana",
    "activo": true,
    "manzanas": 4,
    "predios": 6,
    "lotes": 6
  },
  {
    "id": 3,
    "codigo": "03",
    "nombre": "La Campina",
    "zona": "Urbana",
    "activo": true,
    "manzanas": 3,
    "predios": 3,
    "lotes": 3
  },
  {
    "id": 4,
    "codigo": "04",
    "nombre": "Simbila",
    "zona": "Rustica",
    "activo": true,
    "manzanas": 3,
    "predios": 3,
    "lotes": 3
  }
];
export const MANZANAS = [
  {
    "id": 1,
    "sectorId": 1,
    "sectorCodigo": "01",
    "codigo": "001",
    "predios": 3,
    "lotes": 3
  },
  {
    "id": 2,
    "sectorId": 1,
    "sectorCodigo": "01",
    "codigo": "002",
    "predios": 2,
    "lotes": 2
  },
  {
    "id": 3,
    "sectorId": 1,
    "sectorCodigo": "01",
    "codigo": "003",
    "predios": 2,
    "lotes": 2
  },
  {
    "id": 4,
    "sectorId": 1,
    "sectorCodigo": "01",
    "codigo": "004",
    "predios": 2,
    "lotes": 2
  },
  {
    "id": 5,
    "sectorId": 1,
    "sectorCodigo": "01",
    "codigo": "005",
    "predios": 2,
    "lotes": 2
  },
  {
    "id": 6,
    "sectorId": 2,
    "sectorCodigo": "02",
    "codigo": "001",
    "predios": 1,
    "lotes": 1
  },
  {
    "id": 7,
    "sectorId": 2,
    "sectorCodigo": "02",
    "codigo": "002",
    "predios": 1,
    "lotes": 1
  },
  {
    "id": 8,
    "sectorId": 2,
    "sectorCodigo": "02",
    "codigo": "003",
    "predios": 2,
    "lotes": 2
  },
  {
    "id": 9,
    "sectorId": 2,
    "sectorCodigo": "02",
    "codigo": "004",
    "predios": 2,
    "lotes": 2
  },
  {
    "id": 10,
    "sectorId": 3,
    "sectorCodigo": "03",
    "codigo": "001",
    "predios": 1,
    "lotes": 1
  },
  {
    "id": 11,
    "sectorId": 3,
    "sectorCodigo": "03",
    "codigo": "002",
    "predios": 1,
    "lotes": 1
  },
  {
    "id": 12,
    "sectorId": 3,
    "sectorCodigo": "03",
    "codigo": "003",
    "predios": 1,
    "lotes": 1
  },
  {
    "id": 13,
    "sectorId": 4,
    "sectorCodigo": "04",
    "codigo": "001",
    "predios": 1,
    "lotes": 1
  },
  {
    "id": 14,
    "sectorId": 4,
    "sectorCodigo": "04",
    "codigo": "002",
    "predios": 1,
    "lotes": 1
  },
  {
    "id": 15,
    "sectorId": 4,
    "sectorCodigo": "04",
    "codigo": "003",
    "predios": 1,
    "lotes": 1
  }
];
export const VIAS = [
  {
    "id": 1,
    "codigo": "V-0001",
    "tipo": "AVENIDA",
    "nombre": "Cayetano Heredia",
    "ubigeo": "200104",
    "activa": true
  },
  {
    "id": 2,
    "codigo": "V-0002",
    "tipo": "AVENIDA",
    "nombre": "Progreso",
    "ubigeo": "200104",
    "activa": true
  },
  {
    "id": 3,
    "codigo": "V-0003",
    "tipo": "CALLE",
    "nombre": "Comercio",
    "ubigeo": "200104",
    "activa": true
  },
  {
    "id": 4,
    "codigo": "V-0004",
    "tipo": "CALLE",
    "nombre": "San Francisco",
    "ubigeo": "200104",
    "activa": true
  },
  {
    "id": 5,
    "codigo": "V-0005",
    "tipo": "CALLE",
    "nombre": "Junin",
    "ubigeo": "200104",
    "activa": true
  },
  {
    "id": 6,
    "codigo": "V-0006",
    "tipo": "CALLE",
    "nombre": "Piura",
    "ubigeo": "200104",
    "activa": true
  },
  {
    "id": 7,
    "codigo": "V-0007",
    "tipo": "CALLE",
    "nombre": "Ayacucho",
    "ubigeo": "200104",
    "activa": true
  },
  {
    "id": 8,
    "codigo": "V-0008",
    "tipo": "CALLE",
    "nombre": "Grau",
    "ubigeo": "200104",
    "activa": true
  },
  {
    "id": 9,
    "codigo": "V-0009",
    "tipo": "CALLE",
    "nombre": "Bolognesi",
    "ubigeo": "200104",
    "activa": true
  },
  {
    "id": 10,
    "codigo": "V-0010",
    "tipo": "CALLE",
    "nombre": "Tacna",
    "ubigeo": "200104",
    "activa": true
  },
  {
    "id": 11,
    "codigo": "V-0011",
    "tipo": "JIRON",
    "nombre": "Lima",
    "ubigeo": "200104",
    "activa": true
  },
  {
    "id": 12,
    "codigo": "V-0012",
    "tipo": "JIRON",
    "nombre": "Cusco",
    "ubigeo": "200104",
    "activa": true
  },
  {
    "id": 13,
    "codigo": "V-0013",
    "tipo": "PASAJE",
    "nombre": "Los Ceramistas",
    "ubigeo": "200104",
    "activa": true
  },
  {
    "id": 14,
    "codigo": "V-0014",
    "tipo": "PROLONGACION",
    "nombre": "San Jacinto",
    "ubigeo": "200104",
    "activa": true
  },
  {
    "id": 15,
    "codigo": "V-0015",
    "tipo": "CARRETERA",
    "nombre": "Catacaos - La Legua",
    "ubigeo": "200104",
    "activa": true
  }
];
export type FilaDelPadron = (typeof PADRON)[number];

/* El DETALLE de cada ficha: las 51 filas de `detalle-de-fichas.csv`, agrupadas
   por predio y con las formas de los `record` ANIDADOS de `FichaResource`
   —`ConstruccionResource`, `InstalacionResource`, `EconomicoResource`,
   `BienesComunesResource` y `RuralResource`—.

   Se deriva del archivo por lo mismo que el padron: aqui hay areas, hectareas,
   porcentajes y categorias constructivas, y una errata en cualquiera de ellas
   produce un dato PLAUSIBLE. Los tres agregados que el dominio calcula
   —`areaComunTotal`, `hectareasTotales` y `sinLicencia`— se calculan tambien, y
   con suma exacta de decimales, no en coma flotante: son los mismos que
   `DetalleDeBienesComunes.areaComunTotal()`, `DetalleRural.hectareasTotales()` y
   `DetalleEconomico.sinLicencia()`.

   Las unidades van DENTRO del valor donde el backend las pone —`"42.00 ML"`,
   `"1.0500 HA"`, `"100.00 %"`— y fuera donde no —`"120.40"` de un `AreaM2`—,
   porque eso es lo que distingue una `Medida` de un area en `ConfiguracionDeJson`.

   `20010403003001000000000` —Jiron Cusco 900— NO esta, y no es un olvido: es un
   terreno sin construir, asi que su ficha se queda en la version 1 y con cero
   construcciones. Lo dice la cabecera del propio CSV. */
export const DETALLE_DE_FICHAS = {
  "20010401001001000000000": {
    "tipoFicha": "UNICA",
    "vigenciaDesde": "2026-02-01",
    "origen": "DECLARACION_JURADA",
    "documentoOrigen": "DJ-DEMO-0101",
    "construcciones": [
      {
        "id": 1,
        "piso": "1",
        "areaConstruida": "120.40",
        "anioConstruccion": 1998,
        "material": "LADRILLO",
        "estadoConservacion": "BUENO",
        "categorias": "[CCDCCDC]",
        "porcentajeConstruido": "100.00 %"
      },
      {
        "id": 2,
        "piso": "2",
        "areaConstruida": "86.00",
        "anioConstruccion": 2010,
        "material": "LADRILLO",
        "estadoConservacion": "MUY_BUENO",
        "categorias": "[BBCBBCB]",
        "porcentajeConstruido": "100.00 %"
      }
    ],
    "instalaciones": [
      {
        "id": 1,
        "descripcion": "Cerco perimetrico",
        "unidad": "ML",
        "cantidad": "42.00 ML",
        "anioConstruccion": 1998,
        "estadoConservacion": "REGULAR"
      }
    ],
    "economico": null,
    "bienesComunes": null,
    "rural": null
  },
  "20010401001002000000000": {
    "tipoFicha": "UNICA",
    "vigenciaDesde": "2026-02-01",
    "origen": "DECLARACION_JURADA",
    "documentoOrigen": "DJ-DEMO-0102",
    "construcciones": [
      {
        "id": 1,
        "piso": "1",
        "areaConstruida": "98.30",
        "anioConstruccion": 1991,
        "material": "ADOBE",
        "estadoConservacion": "REGULAR",
        "categorias": "[EEFEEFE]",
        "porcentajeConstruido": "100.00 %"
      }
    ],
    "instalaciones": [],
    "economico": null,
    "bienesComunes": null,
    "rural": null
  },
  "20010401001003000000000": {
    "tipoFicha": "ECONOMICA",
    "vigenciaDesde": "2026-02-01",
    "origen": "DECLARACION_JURADA",
    "documentoOrigen": "DJ-DEMO-0103",
    "construcciones": [
      {
        "id": 1,
        "piso": "1",
        "areaConstruida": "180.00",
        "anioConstruccion": 2015,
        "material": "CONCRETO",
        "estadoConservacion": "MUY_BUENO",
        "categorias": "[AABAABA]",
        "porcentajeConstruido": "100.00 %"
      }
    ],
    "instalaciones": [],
    "economico": {
      "actividades": [
        {
          "id": 1,
          "conductor": "DEMO Ceramica Narihuala S.A.C.",
          "nombreComercial": "Sala de ventas Narihuala",
          "ciiu": "4773",
          "areaOcupada": "180.00",
          "licenciaNumero": null,
          "licenciaFecha": null,
          "anuncioNumero": null,
          "anuncioFecha": null,
          "vigenciaDesde": null
        }
      ],
      "informacionComplementaria": null,
      "sinLicencia": 1
    },
    "bienesComunes": null,
    "rural": null
  },
  "20010401002001000000000": {
    "tipoFicha": "UNICA",
    "vigenciaDesde": "2026-02-01",
    "origen": "FISCALIZACION",
    "documentoOrigen": "ACTA-DEMO-0104",
    "construcciones": [
      {
        "id": 1,
        "piso": "1",
        "areaConstruida": "72.50",
        "anioConstruccion": 1990,
        "material": "ADOBE",
        "estadoConservacion": "MALO",
        "categorias": "[FFGFFGF]",
        "porcentajeConstruido": "100.00 %"
      }
    ],
    "instalaciones": [],
    "economico": null,
    "bienesComunes": null,
    "rural": null
  },
  "20010401002002000000000": {
    "tipoFicha": "ECONOMICA",
    "vigenciaDesde": "2026-02-01",
    "origen": "DECLARACION_JURADA",
    "documentoOrigen": "DJ-DEMO-0105",
    "construcciones": [
      {
        "id": 1,
        "piso": "1",
        "areaConstruida": "240.00",
        "anioConstruccion": 2008,
        "material": "CONCRETO",
        "estadoConservacion": "BUENO",
        "categorias": "[BBCBBCB]",
        "porcentajeConstruido": "100.00 %"
      }
    ],
    "instalaciones": [
      {
        "id": 1,
        "descripcion": "Horno de ceramica",
        "unidad": "UND",
        "cantidad": "2.00 UND",
        "anioConstruccion": 2008,
        "estadoConservacion": "BUENO"
      }
    ],
    "economico": {
      "actividades": [
        {
          "id": 1,
          "conductor": "DEMO Ceramica Narihuala S.A.C.",
          "nombreComercial": "Taller Narihuala",
          "ciiu": "2393",
          "areaOcupada": "240.00",
          "licenciaNumero": null,
          "licenciaFecha": null,
          "anuncioNumero": null,
          "anuncioFecha": null,
          "vigenciaDesde": null
        }
      ],
      "informacionComplementaria": null,
      "sinLicencia": 1
    },
    "bienesComunes": null,
    "rural": null
  },
  "20010401003001000000000": {
    "tipoFicha": "UNICA",
    "vigenciaDesde": "2026-02-01",
    "origen": "DECLARACION_JURADA",
    "documentoOrigen": "DJ-DEMO-0106",
    "construcciones": [
      {
        "id": 1,
        "piso": "1",
        "areaConstruida": "110.00",
        "anioConstruccion": 2001,
        "material": "LADRILLO",
        "estadoConservacion": "BUENO",
        "categorias": "[CCDCCDC]",
        "porcentajeConstruido": "100.00 %"
      }
    ],
    "instalaciones": [],
    "economico": null,
    "bienesComunes": null,
    "rural": null
  },
  "20010401003002000000000": {
    "tipoFicha": "ECONOMICA",
    "vigenciaDesde": "2026-02-01",
    "origen": "DECLARACION_JURADA",
    "documentoOrigen": "DJ-DEMO-0107",
    "construcciones": [
      {
        "id": 1,
        "piso": "1",
        "areaConstruida": "150.00",
        "anioConstruccion": 2012,
        "material": "CONCRETO",
        "estadoConservacion": "BUENO",
        "categorias": "[BBCBBCB]",
        "porcentajeConstruido": "100.00 %"
      }
    ],
    "instalaciones": [],
    "economico": {
      "actividades": [
        {
          "id": 1,
          "conductor": "DEMO Panaderia Simbila S.R.L.",
          "nombreComercial": "Panaderia Simbila",
          "ciiu": "1071",
          "areaOcupada": "150.00",
          "licenciaNumero": null,
          "licenciaFecha": null,
          "anuncioNumero": null,
          "anuncioFecha": null,
          "vigenciaDesde": null
        }
      ],
      "informacionComplementaria": null,
      "sinLicencia": 1
    },
    "bienesComunes": null,
    "rural": null
  },
  "20010401004001000000000": {
    "tipoFicha": "UNICA",
    "vigenciaDesde": "2026-02-01",
    "origen": "DECLARACION_JURADA",
    "documentoOrigen": "DJ-DEMO-0108",
    "construcciones": [
      {
        "id": 1,
        "piso": "1",
        "areaConstruida": "160.00",
        "anioConstruccion": 1994,
        "material": "LADRILLO",
        "estadoConservacion": "BUENO",
        "categorias": "[CCDCCDC]",
        "porcentajeConstruido": "100.00 %"
      },
      {
        "id": 2,
        "piso": "2",
        "areaConstruida": "140.20",
        "anioConstruccion": 2018,
        "material": "CONCRETO",
        "estadoConservacion": "MUY_BUENO",
        "categorias": "[AABAABA]",
        "porcentajeConstruido": "100.00 %"
      }
    ],
    "instalaciones": [],
    "economico": null,
    "bienesComunes": null,
    "rural": null
  },
  "20010401004002000000000": {
    "tipoFicha": "UNICA",
    "vigenciaDesde": "2026-02-01",
    "origen": "DECLARACION_JURADA",
    "documentoOrigen": "DJ-DEMO-0109",
    "construcciones": [
      {
        "id": 1,
        "piso": "1",
        "areaConstruida": "84.00",
        "anioConstruccion": 1990,
        "material": "LADRILLO",
        "estadoConservacion": "REGULAR",
        "categorias": "[DDEDDED]",
        "porcentajeConstruido": "100.00 %"
      }
    ],
    "instalaciones": [],
    "economico": null,
    "bienesComunes": null,
    "rural": null
  },
  "20010401005001000000000": {
    "tipoFicha": "UNICA",
    "vigenciaDesde": "2026-02-01",
    "origen": "DECLARACION_JURADA",
    "documentoOrigen": "DJ-DEMO-0110",
    "construcciones": [
      {
        "id": 1,
        "piso": "1",
        "areaConstruida": "210.00",
        "anioConstruccion": 2005,
        "material": "CONCRETO",
        "estadoConservacion": "BUENO",
        "categorias": "[BBCBBCB]",
        "porcentajeConstruido": "100.00 %"
      },
      {
        "id": 2,
        "piso": "2",
        "areaConstruida": "95.00",
        "anioConstruccion": 2022,
        "material": "CONCRETO",
        "estadoConservacion": "MUY_BUENO",
        "categorias": "[AABAABA]",
        "porcentajeConstruido": "60.00 %"
      }
    ],
    "instalaciones": [
      {
        "id": 1,
        "descripcion": "Piscina",
        "unidad": "UND",
        "cantidad": "1.00 UND",
        "anioConstruccion": 2005,
        "estadoConservacion": "BUENO"
      }
    ],
    "economico": null,
    "bienesComunes": null,
    "rural": null
  },
  "20010401005002000000000": {
    "tipoFicha": "UNICA",
    "vigenciaDesde": "2026-02-01",
    "origen": "DECLARACION_JURADA",
    "documentoOrigen": "DJ-DEMO-0111",
    "construcciones": [
      {
        "id": 1,
        "piso": "1",
        "areaConstruida": "90.00",
        "anioConstruccion": 2000,
        "material": "LADRILLO",
        "estadoConservacion": "BUENO",
        "categorias": "[CCDCCDC]",
        "porcentajeConstruido": "100.00 %"
      }
    ],
    "instalaciones": [],
    "economico": null,
    "bienesComunes": null,
    "rural": null
  },
  "20010402001001000000000": {
    "tipoFicha": "UNICA",
    "vigenciaDesde": "2026-02-01",
    "origen": "FISCALIZACION",
    "documentoOrigen": "ACTA-DEMO-0112",
    "construcciones": [
      {
        "id": 1,
        "piso": "1",
        "areaConstruida": "145.00",
        "anioConstruccion": 1990,
        "material": "LADRILLO",
        "estadoConservacion": "REGULAR",
        "categorias": "[DDEDDED]",
        "porcentajeConstruido": "100.00 %"
      }
    ],
    "instalaciones": [],
    "economico": null,
    "bienesComunes": null,
    "rural": null
  },
  "20010402002001000000000": {
    "tipoFicha": "UNICA",
    "vigenciaDesde": "2026-02-01",
    "origen": "FISCALIZACION",
    "documentoOrigen": "ACTA-DEMO-0113",
    "construcciones": [
      {
        "id": 1,
        "piso": "1",
        "areaConstruida": "60.00",
        "anioConstruccion": 1990,
        "material": "QUINCHA",
        "estadoConservacion": "MALO",
        "categorias": "[GGHGGHG]",
        "porcentajeConstruido": "100.00 %"
      }
    ],
    "instalaciones": [],
    "economico": null,
    "bienesComunes": null,
    "rural": null
  },
  "20010402003001010103201": {
    "tipoFicha": "BIENES_COMUNES",
    "vigenciaDesde": "2026-02-01",
    "origen": "DECLARACION_JURADA",
    "documentoOrigen": "DJ-DEMO-0114",
    "construcciones": [
      {
        "id": 1,
        "piso": "3",
        "areaConstruida": "64.20",
        "anioConstruccion": 2019,
        "material": "CONCRETO",
        "estadoConservacion": "MUY_BUENO",
        "categorias": "[AABAABA]",
        "porcentajeConstruido": "100.00 %"
      }
    ],
    "instalaciones": [],
    "economico": null,
    "bienesComunes": {
      "bienes": [
        {
          "id": 1,
          "descripcion": "Escalera y hall de ingreso",
          "area": "48.00",
          "material": "CONCRETO",
          "estadoConservacion": "MUY_BUENO",
          "anioConstruccion": 2019
        },
        {
          "id": 2,
          "descripcion": "Azotea comun",
          "area": "120.00",
          "material": "CONCRETO",
          "estadoConservacion": "BUENO",
          "anioConstruccion": 2019
        }
      ],
      "participaciones": [
        {
          "predioId": 14,
          "porcentaje": "50.00 %"
        },
        {
          "predioId": 15,
          "porcentaje": "50.00 %"
        }
      ],
      "areaComunTotal": "168.00"
    },
    "rural": null
  },
  "20010402003002010104401": {
    "tipoFicha": "BIENES_COMUNES",
    "vigenciaDesde": "2026-02-01",
    "origen": "DECLARACION_JURADA",
    "documentoOrigen": "DJ-DEMO-0115",
    "construcciones": [
      {
        "id": 1,
        "piso": "4",
        "areaConstruida": "64.20",
        "anioConstruccion": 2019,
        "material": "CONCRETO",
        "estadoConservacion": "MUY_BUENO",
        "categorias": "[AABAABA]",
        "porcentajeConstruido": "100.00 %"
      }
    ],
    "instalaciones": [],
    "economico": null,
    "bienesComunes": {
      "bienes": [
        {
          "id": 1,
          "descripcion": "Escalera y hall de ingreso",
          "area": "48.00",
          "material": "CONCRETO",
          "estadoConservacion": "MUY_BUENO",
          "anioConstruccion": 2019
        }
      ],
      "participaciones": [
        {
          "predioId": 14,
          "porcentaje": "50.00 %"
        },
        {
          "predioId": 15,
          "porcentaje": "50.00 %"
        }
      ],
      "areaComunTotal": "48.00"
    },
    "rural": null
  },
  "20010402004001000000000": {
    "tipoFicha": "ECONOMICA",
    "vigenciaDesde": "2026-02-01",
    "origen": "DECLARACION_JURADA",
    "documentoOrigen": "DJ-DEMO-0116",
    "construcciones": [
      {
        "id": 1,
        "piso": "1",
        "areaConstruida": "380.00",
        "anioConstruccion": 2014,
        "material": "CONCRETO",
        "estadoConservacion": "BUENO",
        "categorias": "[BBCBBCB]",
        "porcentajeConstruido": "100.00 %"
      }
    ],
    "instalaciones": [
      {
        "id": 1,
        "descripcion": "Patio pavimentado",
        "unidad": "M2",
        "cantidad": "320.00 M2",
        "anioConstruccion": 2014,
        "estadoConservacion": "BUENO"
      }
    ],
    "economico": {
      "actividades": [
        {
          "id": 1,
          "conductor": "DEMO Querevalu Eche Segundo",
          "nombreComercial": "Almacen Querevalu",
          "ciiu": "5210",
          "areaOcupada": "380.00",
          "licenciaNumero": null,
          "licenciaFecha": null,
          "anuncioNumero": null,
          "anuncioFecha": null,
          "vigenciaDesde": null
        }
      ],
      "informacionComplementaria": null,
      "sinLicencia": 1
    },
    "bienesComunes": null,
    "rural": null
  },
  "20010402004002000000000": {
    "tipoFicha": "UNICA",
    "vigenciaDesde": "2026-02-01",
    "origen": "FISCALIZACION",
    "documentoOrigen": "ACTA-DEMO-0117",
    "construcciones": [
      {
        "id": 1,
        "piso": "1",
        "areaConstruida": "102.00",
        "anioConstruccion": 1992,
        "material": "LADRILLO",
        "estadoConservacion": "REGULAR",
        "categorias": "[DDEDDED]",
        "porcentajeConstruido": "100.00 %"
      }
    ],
    "instalaciones": [],
    "economico": null,
    "bienesComunes": null,
    "rural": null
  },
  "20010403001001000000000": {
    "tipoFicha": "ECONOMICA",
    "vigenciaDesde": "2026-02-01",
    "origen": "DECLARACION_JURADA",
    "documentoOrigen": "DJ-DEMO-0118",
    "construcciones": [
      {
        "id": 1,
        "piso": "1",
        "areaConstruida": "220.00",
        "anioConstruccion": 2011,
        "material": "CONCRETO",
        "estadoConservacion": "BUENO",
        "categorias": "[BBCBBCB]",
        "porcentajeConstruido": "100.00 %"
      }
    ],
    "instalaciones": [
      {
        "id": 1,
        "descripcion": "Patio de maniobras",
        "unidad": "M2",
        "cantidad": "235.00 M2",
        "anioConstruccion": 2011,
        "estadoConservacion": "REGULAR"
      }
    ],
    "economico": {
      "actividades": [
        {
          "id": 1,
          "conductor": "DEMO Transportes La Legua E.I.R.L.",
          "nombreComercial": "Patio La Legua",
          "ciiu": "4923",
          "areaOcupada": "455.00",
          "licenciaNumero": null,
          "licenciaFecha": null,
          "anuncioNumero": null,
          "anuncioFecha": null,
          "vigenciaDesde": null
        }
      ],
      "informacionComplementaria": null,
      "sinLicencia": 1
    },
    "bienesComunes": null,
    "rural": null
  },
  "20010403002001000000000": {
    "tipoFicha": "UNICA",
    "vigenciaDesde": "2026-02-01",
    "origen": "DECLARACION_JURADA",
    "documentoOrigen": "DJ-DEMO-0119",
    "construcciones": [
      {
        "id": 1,
        "piso": "1",
        "areaConstruida": "130.00",
        "anioConstruccion": 2003,
        "material": "LADRILLO",
        "estadoConservacion": "BUENO",
        "categorias": "[CCDCCDC]",
        "porcentajeConstruido": "100.00 %"
      }
    ],
    "instalaciones": [],
    "economico": null,
    "bienesComunes": null,
    "rural": null
  },
  "20010404001001000000000": {
    "tipoFicha": "RURAL",
    "vigenciaDesde": "2026-02-01",
    "origen": "MIGRACION",
    "documentoOrigen": "MIG-DEMO-0120",
    "construcciones": [],
    "instalaciones": [],
    "economico": null,
    "bienesComunes": null,
    "rural": {
      "tierras": [
        {
          "id": 1,
          "clasificacion": "CULTIVO_EN_LIMPIO",
          "calidadAgrologica": "A1",
          "riego": "BAJO_RIEGO",
          "hectareas": "1.0500 HA",
          "hectareasComunes": null
        },
        {
          "id": 2,
          "clasificacion": "PASTOS",
          "calidadAgrologica": "P2",
          "riego": "SECANO",
          "hectareas": "0.2000 HA",
          "hectareasComunes": null
        }
      ],
      "colindantes": [
        {
          "orientacion": "NORTE",
          "descripcion": "Predio de DEMO Sucesion Panta"
        },
        {
          "orientacion": "SUR",
          "descripcion": "Canal de regadio La Legua"
        },
        {
          "orientacion": "ESTE",
          "descripcion": "Carretera Catacaos - La Legua"
        },
        {
          "orientacion": "OESTE",
          "descripcion": "Fundo vecino sin identificar"
        }
      ],
      "hectareasTotales": "1.2500 HA"
    }
  },
  "20010404002001000000000": {
    "tipoFicha": "RURAL",
    "vigenciaDesde": "2026-02-01",
    "origen": "MIGRACION",
    "documentoOrigen": "MIG-DEMO-0121",
    "construcciones": [],
    "instalaciones": [],
    "economico": null,
    "bienesComunes": null,
    "rural": {
      "tierras": [
        {
          "id": 1,
          "clasificacion": "CULTIVO_EN_LIMPIO",
          "calidadAgrologica": "A2",
          "riego": "BAJO_RIEGO",
          "hectareas": "0.8300 HA",
          "hectareasComunes": null
        }
      ],
      "colindantes": [
        {
          "orientacion": "NORTE",
          "descripcion": "Canal de regadio La Legua"
        },
        {
          "orientacion": "SUR",
          "descripcion": "Fundo Simbila Chico"
        }
      ],
      "hectareasTotales": "0.8300 HA"
    }
  },
  "20010404003001000000000": {
    "tipoFicha": "RURAL",
    "vigenciaDesde": "2026-02-01",
    "origen": "MIGRACION",
    "documentoOrigen": "MIG-DEMO-0122",
    "construcciones": [],
    "instalaciones": [],
    "economico": null,
    "bienesComunes": null,
    "rural": {
      "tierras": [
        {
          "id": 1,
          "clasificacion": "CULTIVO_PERMANENTE",
          "calidadAgrologica": "B1",
          "riego": "BAJO_RIEGO",
          "hectareas": "1.9400 HA",
          "hectareasComunes": "0.1500 HA"
        },
        {
          "id": 2,
          "clasificacion": "ERIAZO",
          "calidadAgrologica": null,
          "riego": "SECANO",
          "hectareas": "0.2000 HA",
          "hectareasComunes": null
        }
      ],
      "colindantes": [
        {
          "orientacion": "ESTE",
          "descripcion": "Carretera Catacaos - La Legua"
        }
      ],
      "hectareasTotales": "2.1400 HA"
    }
  }
};
