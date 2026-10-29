# Sonora — música para vídeo

Aplicação web em português: carrega áudio, título e imagem opcional e gera MP4 H.264/AAC com ondas sonoras. Formatos horizontal e vertical. Sem API paga.

## Executar

Requisitos: Python 3.12+ e FFmpeg com libx264, AAC, showwaves e drawtext (inclui ffprobe).

```sh
cd /workspace/app-videos-musicas
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
.venv/bin/python app.py
```

O serviço usa a porta 8000 e endereço local por omissão. `HOST` e `PORT` permitem configurar o endereço e a porta. Não é necessário compilar o frontend.

## Limites desta primeira versão

- Upload total de 100 MB; músicas até 10 minutos; uma conversão de cada vez, até três pedidos pendentes.
- Áudios e imagens de entrada são removidos após processamento. MP4s ficam em `.runtime/` até remoção manual quando não houver trabalhos ativos.
- Estado dos trabalhos em memória: reiniciar o servidor invalida as ligações anteriores.
- Servidor para desenvolvimento e utilização privada. Alojamento público requer servidor de produção, autenticação, quotas e limpeza automática de vídeos. Não inclui geração de cenas com IA nem publicação nas redes sociais.
- As ferramentas são gratuitas; alojamento e recursos de processamento podem ter custos.

## Validação

```sh
.venv/bin/python -m unittest discover -s tests -v
```

Os testes usam FFmpeg para gerar áudio e verificam a conversão real em MP4 nos dois formatos, download e rejeição de ficheiros inválidos.
