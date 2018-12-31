package com.example.diamond.tscamera;


class ASN1_OBJ {

    byte	head_	= 0;			///< ヘッダ本体のコピー
    byte	class_	= 0;			///< クラス情報(CLS_TAGTYPE/CLS_CONTEXTSPECIFIC等)
    boolean	construct_ = false;		///< trueなら構造型
    byte 	tag_	= 0;			///< タグ種類(DERTag)
    int  	pos_	= 0;			///< data中の開始位置
    int		len_	= 0;			///< 値サイズ
    byte[]  value_	= null;			///< 値/オブジェクトのバイト配列
}
