# AI 瀵硅瘽鍐涘笀 / Chat Copilot V1

## 浜у搧杞瀷

鍘熲€淎I鎭嬬埍澶у笀鈥濅粠鐙珛鍜ㄨ鑱婂ぉ椤碉紝杞瀷涓洪潰鍚戠湡瀹炶亰澶╁満鏅殑鈥淎I 瀵硅瘽鍐涘笀鈥濄€?
鏍稿績鐩爣涓嶆槸鏇跨敤鎴疯亰澶╋紝鑰屾槸涓虹敤鎴锋彁渚涗笁鏉″苟鍒椼€佸彲鐩存帴澶嶅埗鐨勫弬鑰冨洖澶嶏細
- A锝滄縺杩涳細鏇翠富鍔ㄣ€佹洿鏄庣‘鍦版帹杩涳紱
- B锝滄甯革細鑷劧銆佸潎琛★紝榛樿寤鸿锛?- C锝滀繚瀹堬細鏇村厠鍒讹紝缁欏鏂规洿澶氱┖闂淬€?
浠讳綍娓犻亾閮戒笉鑷姩鏇跨敤鎴峰彂閫佹秷鎭€?
## 鏍稿績鍘熷垯

鍙傝€?goutoujunshi 鐨?SKILL 涓?practical锛?- 涓€娆″彧瑙ｅ喅涓€涓富鐩爣锛?- 绗竴灞忓厛缁欏彲鐩存帴鍙戦€佺殑棣栭€夊洖澶嶏紱
- 鍙妸鍙鍘熸枃褰撲簨瀹烇紝涓嶈蹇冿紱
- 鍥炲闀垮害銆佺敤璇嶃€佷翰瀵嗗害瑕佽创杩戠敤鎴峰彛鍚伙紱
- 缁跨伅鍙帹杩涗竴灏忔锛岄粍鐏敹涓€鐐癸紝绾㈢伅鍋滄锛?- 涓嶄娇鐢ㄨ船浣庛€佸珘濡掓搷鎺с€佸け鑱旀祴璇曘€佽櫄鍋囩█缂虹瓑绛栫暐銆?
## 娓犻亾绛栫暐

### Android 鎵嬫満绔?`apps/android` 鏄井淇″拰 QQ 鍏辩敤鐨勬墜鏈虹涓绘柟妗堬細
- 鐢ㄦ埛涓诲姩鍒嗕韩 / 閫変腑鑱婂ぉ鏂囧瓧锛?- 缁熶竴璋冪敤 ConversationCoachService锛?- 鍦ㄥ綋鍓嶅井淇℃垨 QQ 鑱婂ぉ鐣岄潰涓婃柟鏄剧ず A/B/C锛?- 鐐瑰嚮鏌愭潯鍙鍒跺埌绯荤粺鍓创鏉匡紝涓嶈嚜鍔ㄥ彂閫併€?
### Web
Web 鏄嫭绔嬬綉椤电増锛屼笉鍖哄垎瀵硅瘽鏉ヨ嚜寰俊杩樻槸 QQ銆?鐢ㄦ埛鐩存帴绮樿创鏈€杩戝璇濓紝缁熶竴浣跨敤 `platform=web`銆?
### QQ 瀹樻柟鏈哄櫒浜?缁х画淇濈暀涓哄彲閫?QQ 鍏ュ彛锛屼笉鏇夸唬 Android 鎵嬫満鍔╂墜銆?
## V1 璇锋眰妯″瀷

杈撳叆鑷冲皯鍖呭惈锛?- platform锛氫富娴佺▼浣跨敤 web / android锛決Q 瀹樻柟鏈哄櫒浜哄彲浣跨敤 qq锛?- conversationId锛?- userAlias锛?- otherAlias锛?- relationshipStage锛?- goal锛?- userStyle锛?- 鏈€杩戣嫢骞叉潯鐪熷疄娑堟伅銆?
## V1 杩斿洖妯″瀷

杩斿洖缁撴瀯锛?- needsClarification锛?- clarificationQuestion锛?- mainStrategy锛?- diagnosis锛?- bestReply锛氬浐瀹氭槧灏勪负 B锝滄甯革紱
- alternatives锛氬浐瀹氫负 A锝滄縺杩涖€丆锝滀繚瀹堬紱
- positive / ambiguous / reject 涓変釜鍙嶉鍒嗘敮锛?- nextStep锛?- facts锛?- uncertainties锛?- safetyNotes銆?
## 缁熶竴澶勭悊閾捐矾

```text
Web 绮樿创瀵硅瘽              Android 寰俊 / QQ
     鈫?                        鈫? platform=web           platform=android
     鈹斺攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹攢鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹€鈹?                鈫?      Conversation Coach Core
                鈫?      A 婵€杩?/ B 姝ｅ父 / C 淇濆畧
          鈫?                鈫?      Web 涓夊崱鐗?       Android 鎮诞涓夊崱鐗?          鈫?                鈫?              鐢ㄦ埛鐐瑰嚮澶嶅埗
```

QQ 瀹樻柟鏈哄櫒浜轰綔涓洪澶栧叆鍙ｇ户缁鐢ㄥ悓涓€涓?ConversationCoachService銆?