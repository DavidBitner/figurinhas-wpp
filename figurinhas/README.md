# Figurinhas

App Android para criar figurinhas do WhatsApp sem a borda branca do editor do próprio WhatsApp e sem anúncios. As figurinhas são entregues ao WhatsApp pela API oficial de pacotes de figurinhas, então entram como estão.

## Como usar

1. Abra o app e toque em **Novo pacote** (marque "animado" se for de GIFs/vídeos).
2. Toque em **Adicionar fotos** (ou **Adicionar GIFs/vídeos**) e escolha vários arquivos de uma vez.
3. Em pacotes estáticos, deixe marcada a opção de remover o fundo para recortar o objeto automaticamente.
4. Com pelo menos 3 figurinhas, toque em **Adicionar ao WhatsApp**.
5. No WhatsApp, abra o pacote nos favoritos/figurinhas e marque as que quiser.

Toque e segure uma figurinha para removê-la.

## Limites do WhatsApp (o app já respeita)

- 3 a 30 figurinhas por pacote, todas estáticas ou todas animadas.
- Estáticas: WebP 512×512 com até 100 KB.
- Animadas: WebP animado 512×512, até 500 KB e até 10 s.

## Gerar o APK

O workflow `.github/workflows/build-apk.yml` compila o app a cada push na branch `main` e publica o `figurinhas.apk` na aba **Releases** do repositório. Baixe esse arquivo no celular e instale (permita "instalar apps de fontes desconhecidas" quando o Android pedir).

O APK é assinado com `app/stickers.keystore`, uma chave de uso pessoal que fica no repositório. Com a mesma chave, novas versões instalam por cima das anteriores sem perder os pacotes.

## Notas

- A remoção de fundo usa o ML Kit (Google Play Services) e baixa o modelo na primeira vez, então precisa de internet nesse momento.
- GIFs e vídeos são convertidos para WebP animado dentro do app; a remoção de fundo ainda não funciona em animações.
- Partes do código derivam do app de exemplo do WhatsApp (licença BSD, em `LICENSE-WhatsApp-Stickers.txt`).
