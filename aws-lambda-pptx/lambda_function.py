import json
import os
import boto3
import urllib.request
import urllib.parse
import tempfile
import subprocess
import fitz  # PyMuPDF

s3_client = boto3.client('s3')

# Variables de Entorno Configadas en la Lambda
WEBHOOK_URL = os.environ.get('SLIDEHUB_WEBHOOK_URL')
WEBHOOK_SECRET = os.environ.get('SLIDEHUB_WEBHOOK_SECRET')
LIBREOFFICE_PATH = os.environ.get('LIBREOFFICE_PATH', '/opt/instdir/program/soffice.bin')

def lambda_handler(event, context):
    print(f"Evento recibido: {json.dumps(event)}")
    
    try:
        # Extraer info del evento S3
        record = event['Records'][0]
        bucket = record['s3']['bucket']['name']
        raw_key = urllib.parse.unquote_plus(record['s3']['object']['key'])
        
        # Validar correcta ubicación (raw-pptx/{presentationId}.pptx)
        if not raw_key.startswith('raw-pptx/') or not raw_key.endswith('.pptx'):
            print(f"Ignorando archivo no válido: {raw_key}")
            return {'statusCode': 400, 'body': 'Ruta o formato no válido'}
            
        presentation_id = raw_key.split('/')[1].replace('.pptx', '')
        
        with tempfile.TemporaryDirectory() as temp_dir:
            file_path = os.path.join(temp_dir, f"{presentation_id}.pptx")
            pdf_path = os.path.join(temp_dir, f"{presentation_id}.pdf")
            
            # Descargar archivo pptx
            print(f"Descargando {raw_key} de {bucket}")
            s3_client.download_file(bucket, raw_key, file_path)
            
            # Convertir PPTX a PDF usando LibreOffice (invocado como subproceso headless)
            print("Convirtiendo PPTX a PDF usando LibreOffice...")
            lo_cmd = [
                LIBREOFFICE_PATH,
                '--headless',
                '--invisible',
                '--nodefault',
                '--nofirststartwizard',
                '--convert-to', 'pdf',
                '--outdir', temp_dir,
                file_path
            ]
            subprocess.run(lo_cmd, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            
            if not os.path.exists(pdf_path):
                raise Exception("La conversión a PDF falló silenciosamente.")
                
            # Convertir PDF a Arrays de PNGs (diapositivas) con PyMuPDF
            print("Convirtiendo PDF a PNGs...")
            doc = fitz.open(pdf_path)
            total_slides = len(doc)
            
            uploaded_keys = []
            
            # El for genera Slide_1.PNG en adelante
            for i in range(total_slides):
                page = doc.load_page(i)
                # Zoom (dpi alto) para mejor resolución [resolución = matrix 2.0]
                pix = page.get_pixmap(matrix=fitz.Matrix(2.0, 2.0))
                png_path = os.path.join(temp_dir, f"Slide_{i+1}.PNG")
                pix.save(png_path)
                
                # Subir PNG al S3 en la carpeta final (alineado con buildSlideKey de Java)
                target_key = f"slides/{presentation_id}/{i+1}.png"
                s3_client.upload_file(
                    png_path, bucket, target_key,
                    ExtraArgs={'ContentType': 'image/png'}
                )
                uploaded_keys.append(target_key)
                
            print(f"Éxito: {total_slides} diapositivas extraídas y subidas S3.")

            # Limpiar el PPTX original para no acumular almacenamiento
            s3_client.delete_object(Bucket=bucket, Key=raw_key)
            print(f"raw-pptx eliminado: {raw_key}")

            # Notificar al Backend de SlideHub
            send_webhook(presentation_id, total_slides, "READY")
            
            return {
                'statusCode': 200,
                'body': json.dumps({'total_slides': total_slides})
            }
            
    except Exception as e:
        print(f"Error procesando el archivo: {str(e)}")
        # Intentar notificar error si tenemos el presentation_id
        if 'presentation_id' in locals():
            send_webhook(presentation_id, 0, "FAILED", error=str(e))
        raise e

def send_webhook(presentation_id, slide_count, status, error=None):
    if not WEBHOOK_URL:
        print("WEBHOOK_URL no definida, omitiendo callback.")
        return
        
    payload = {
        "presentationId": presentation_id,
        "slideCount": slide_count,
        "status": status,
        "error": error
    }
    
    req = urllib.request.Request(
        url=WEBHOOK_URL,
        data=json.dumps(payload).encode('utf-8'),
        headers={
            'Content-Type': 'application/json',
            'X-Webhook-Secret': WEBHOOK_SECRET
        },
        method='POST'
    )
    
    try:
        with urllib.request.urlopen(req) as response:
            resp_body = response.read()
            print(f"Webhook enviado. Respuesta: {response.status} - {resp_body.decode('utf-8')}")
    except Exception as e:
        print(f"Fallo enviando webhook: {str(e)}")
