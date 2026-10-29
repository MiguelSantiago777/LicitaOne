# Incluído em cada location: um add_header dentro de location anula os do nível server
add_header X-Content-Type-Options nosniff always;
add_header Referrer-Policy no-referrer always;
add_header X-Frame-Options DENY always;
add_header Content-Security-Policy "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; font-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'" always;
add_header Permissions-Policy "camera=(), microphone=(), geolocation=(), payment=(), usb=()" always;
# Só vale sob HTTPS (o navegador ignora em HTTP). Em produção, o TLS termina no proxy da frente ou aqui.
add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
