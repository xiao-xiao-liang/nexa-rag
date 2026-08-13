package com.nexarag.document.model.bo.structure;

/** MinerU PDF 标题块携带的相对版式证据。 */
public record PdfTitleLayoutBO(String title, int sequence, Integer pageNumber, Double fontSize) {
}
