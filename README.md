# Figurinhas

App Android para criar figurinhas do WhatsApp sem a borda branca do editor do próprio WhatsApp e sem anúncios. As figurinhas são entregues pela API oficial de pacotes de figurinhas, então entram no WhatsApp exatamente como foram geradas.

## Recursos

- Pacotes estáticos (fotos) e animados (GIFs e vídeos).
- Remoção automática de fundo com recorte do objeto (ML Kit), para fotos.
- Importação de vários arquivos de uma vez.
- Conversão para os formatos exigidos pelo WhatsApp: WebP 512×512, até 100 KB nas estáticas e até 500 KB e 10 s nas animadas.
- Adição ao WhatsApp e ao WhatsApp Business.

## Instalação

1. Abra a aba **Releases** deste repositório e baixe o `figurinhas.apk` mais recente.
2. Instale no celular. Na primeira vez, o Android pede para permitir a instalação de fontes desconhecidas.

Requer Android 7.0 ou superior.

## Como usar

1. Toque em **Novo pacote** e escolha o nome. Marque "animado" para pacotes de GIFs e vídeos.
2. Toque em **Adicionar fotos** (ou **Adicionar GIFs/vídeos**) e selecione os arquivos.
3. Em pacotes estáticos, deixe marcada a opção de remover o fundo para recortar o objeto automaticamente.
4. Com pelo menos 3 figurinhas, toque em **Adicionar ao WhatsApp**.

Toque e segure uma figurinha para removê-la.

## Limites do WhatsApp

- De 3 a 30 figurinhas por pacote.
- Todas estáticas ou todas animadas no mesmo pacote.

## Compilar

O workflow em `.github/workflows/build-apk.yml` compila o app a cada push na `main` e publica o `figurinhas.apk` em **Releases**.

Para compilar localmente, use JDK 17 e o Android SDK 34:

```
./gradlew assembleRelease
```

O APK sai em `app/build/outputs/apk/release/`.

## Estrutura

- `app/src/main/java/com/davidbitner/stickers/`
  - `MainActivity` e `PackActivity`: telas de pacotes e de edição.
  - `StickerProcessor`: conversão de imagens, GIFs e vídeos para WebP.
  - `AnimatedWebp`: montagem do WebP animado a partir dos quadros.
  - `BackgroundRemover`: remoção de fundo com ML Kit.
  - `PackStore`: armazenamento dos pacotes no aparelho.
  - `StickerContentProvider` e `WhatsApp`: entrega dos pacotes ao WhatsApp.
- `app/stickers.keystore`: chave de assinatura de uso pessoal. Com a mesma chave, novas versões instalam por cima das anteriores sem perder os pacotes.

## Notas

- A remoção de fundo baixa o modelo do Google Play Services na primeira vez, então precisa de internet nesse momento.
- A remoção de fundo ainda não funciona em GIFs e vídeos.

## Licença

MIT, em `LICENSE`. Partes do código derivam do app de exemplo do WhatsApp, sob licença BSD, em `LICENSE-WhatsApp-Stickers.txt`.
