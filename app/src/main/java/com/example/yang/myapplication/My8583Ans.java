package com.example.yang.myapplication;

import java.util.Arrays;

import static com.example.yang.myapplication.DesUtil.DES_decrypt_3;
import static com.example.yang.myapplication.DesUtil.DES_encrypt;
import static com.example.yang.myapplication.DesUtil.DES_encrypt_3;
import static java.lang.System.arraycopy;

public class My8583Ans extends Easy8583Ans {

    private static final String TAG= " My8583Ans";

    //通信涉及到的一些参数,内部使用
    private static long commSn = 1; //通讯流水号
    private static byte[] piciNum = new byte[3];//批次号
    private static byte[] licenceNum = {0x33,0x30,0x36,0x30};//入网许可证编号

    //需要外部设置的参数有：商户号，终端号，主秘钥，TPDU(以下的为默认值，并提供set和get方法)
    private static String manNum  = "001430170119999"; //商户号
    private static String posNum  = "99999906"; //终端号
    private static String mainKey = "258FB0Ab70D025CDB99DF2C4D302D646"; //主秘钥
    private static String TPDU    = "6005010000";
    private static String macKey ; //工作秘钥

    My8583Ans(){

        //通过子类修改父类的配置
        pack.tpdu = hexStringToBytes(TPDU);
    }
    /**
     * 签到报文组帧
     * @param field
     * @param tx
     */
    public void frame8583QD( __8583Fields[] field, Pack tx){

        //消息类型
        tx.msgType[0] = 0x08;
        tx.msgType[1] = 0x00;
        //11域，受卡方系统跟踪号BCD 通讯流水
        field[10].ishave = 1;
        field[10].len = 3;
        String tmp = String.format("%06d",commSn);
        field[10].data = hexStringToBytes(tmp);
        //41域，终端号
        field[40].ishave = 1;
        field[40].len = 8;
        field[40].data = posNum.getBytes();
        //42域，商户号
        field[41].ishave = 1;
        field[41].len = 15;
        field[41].data = manNum.getBytes();
        //60域
        field[59].ishave = 1;
        field[59].len = 0x11;
        field[59].data = new byte[6];
        field[59].data[0] = 0x00;
        arraycopy(piciNum,0,field[59].data,1,3);
        field[59].data[4] = 0x00;
        field[59].data[5] = 0x30;
        //62域
        field[61].ishave = 1;
        field[61].len = 0x25;
        field[61].data = new byte[25];
        String str = "Sequence No12";
        arraycopy(str.getBytes(),0,field[61].data,0,13);
        arraycopy(licenceNum,0,field[61].data,13,4);
        arraycopy(posNum.getBytes(),0,field[61].data,17,8);
        //63域
        field[62].ishave = 1;
        field[62].len = 0x03;
        field[62].data = new byte[3];
        field[62].data[0] = 0x30;
        field[62].data[1] = 0x30;
        field[62].data[2] = 0x31;
        /*报文组帧，自动组织这些域到Pack的TxBuffer中*/
        pack8583Fields(field,tx);
        commSn++; //通讯流水每次加一
    }

    /**
     * 8583签到的响应报文解析
     * @param rxbuf
     * @param rxlen
     * @return 0，成功 非0，失败
     */
    public int ans8583QD(byte[] rxbuf,int rxlen){

        int ret = 0;
        ret = ans8583Fields(rxbuf,rxlen,fieldsRecv);
        if(ret != 0) {
            //Log.d(TAG,"解析失败！");
            System.out.println("<-Er 解析失败！");
            return ret;
        }
        //Log.d(TAG,"解析成功！");
        System.out.println("->ok 解析成功！");
        //消息类型判断
        if((pack.msgType[0] != 0x08)||(pack.msgType[1]!= 0x10)) {
            //Log.d(TAG,"消息类型错！");
            return 2;
        }
        //应答码判断
        if((fieldsRecv[38].data[0] != 0x30)||(fieldsRecv[38].data[1] != 0x30)){
            //Log.d(TAG,"应答码不正确！");
            //Log.d(TAG,String.format("应答码:%02x%02x",fieldsRecv[38].data[0],fieldsRecv[38].data[1]));
            return 3;
        }
        //跟踪号比较
        if(!Arrays.equals(fieldsSend[10].data,fieldsRecv[10].data)){
            //return 4;
        }
        //终端号比较
        if(!Arrays.equals(fieldsSend[40].data,fieldsRecv[40].data)){
            //return 5;
        }
        //商户号比较
        if(!Arrays.equals(fieldsSend[41].data,fieldsRecv[41].data)){
            //return 6;
        }
        //3DES解密PIN KEY
        byte[] data = new byte[16];
        arraycopy(fieldsRecv[61].data,0,data,0,16);
        byte[] pinkey = DES_decrypt_3(data,hexStringToBytes(mainKey));
        //解密后的结果对8Byte全0做3DES加密运算
        System.out.println("pinkey:"+bytesToHexString(pinkey));
        byte[] tmp= {0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00};
        byte[] out =  DES_encrypt_3(tmp,pinkey);
        //对比pincheck是否一致
        byte[] check = new byte[4];
        byte[] pincheck = new byte[4];
        arraycopy(out,0,check,0,4);
        arraycopy(fieldsRecv[61].data,16,pincheck,0,4);
        if(!Arrays.equals(check,pincheck)) {
            System.out.println("<-Er PIK错误");
            return 7;
        }
        else {
            System.out.println("<-ok PIK正确");
        }
        //3DES解密MAC KEY
        arraycopy(fieldsRecv[61].data,20,data,0,16);
        byte[] mackey = DES_decrypt_3(data,hexStringToBytes(mainKey));
        //解密后的结果对8Byte全0做DES加密运算
        System.out.println("mackey:"+bytesToHexString(mackey));
        out =  DES_encrypt(tmp,mackey);

        byte[] maccheck = new byte[4];
        arraycopy(out,0,check,0,4);
        arraycopy(fieldsRecv[61].data,36,maccheck,0,4);
        if(!Arrays.equals(check,maccheck)) {
            System.out.println("<-Er MAC错误");
            return 8;
        }
        else {
            System.out.println("<-ok MAC正确");
            macKey = bytesToHexString(mackey);
        }
        //签到成功
        return 0;

    }
    /**
     * 银联二维码组包
     * @param field
     * @param tx
     */
    public void frame8583QrcodeUp( __8583Fields[] field, Pack tx){

        //消息类型
        tx.msgType[0] = 0x02;
        tx.msgType[1] = 0x00;
        //3域 交易处理码

        //4域 交易金额

        //11域，受卡方系统跟踪号BCD 通讯流水
        field[10].ishave = 1;
        field[10].len = 3;
        String tmp = String.format("%06d",commSn);
        field[10].data = hexStringToBytes(tmp);

        //22域

        // 25域

        //
        //41域，终端号
        field[40].ishave = 1;
        field[40].len = 8;
        field[40].data = posNum.getBytes();
        //42域，商户号
        field[41].ishave = 1;
        field[41].len = 15;
        field[41].data = manNum.getBytes();

        //49域 交易货币代码


        //60域
        field[59].ishave = 1;
        field[59].len = 0x13;
        field[59].data = new byte[7];
        field[59].data[0] = 0x22;
        arraycopy(piciNum,0,field[59].data,1,3);
        field[59].data[4] = 0x00;
        field[59].data[5] = 0x06;
        field[59].data[6] = 0x00;
        //MAC
        //UP_Get_MAC( &buf[13], len-13, UP_SysCfg.MACkek, &buf[len] );
        /*报文组帧，自动组织这些域到Pack的TxBuffer中*/
        pack8583Fields(field,tx);
    }

    public static void main(String[] args) {
        My8583Ans mypack = new My8583Ans();
        //签到组包
        mypack.frame8583QD(mypack.fieldsSend,mypack.pack);
        System.out.println(mypack.pack.toString());
        System.out.println(mypack.getFields(mypack.fieldsSend));
        //打印出待发送的报文
        byte[] send = new byte[mypack.pack.txLen];
        arraycopy(mypack.pack.txBuffer,0,send,0,mypack.pack.txLen);
        System.out.println(My8583Ans.bytesToHexString(send));

        //接收解析,假如收到的报文在recv中
        String recvstr ="007960000001386131003111080810003800010AC0001450021122130107200800085500323231333031343931333239303039393939393930363030313433303137303131393939390011000005190030004046F161A743497B32EAC760DF5EA57DF5900ECCE3977731A7EA402DDF0000000000000000CFF1592A";
        byte[] recv =My8583Ans.hexStringToBytes(recvstr);
        // mypack.ans8583Fields(bt,bt.length,mypack.fieldsRecv);
        //解析
        mypack.ans8583QD(recv,recv.length);
        //打印出解析成功的各个域
        System.out.println(mypack.getFields(mypack.fieldsRecv));
    }


    public static void setManNum(String manNum) {
        My8583Ans.manNum = manNum;
    }

    public static void setPosNum(String posNum) {
        My8583Ans.posNum = posNum;
    }

    public static void setMainKey(String mainKey) {
        My8583Ans.mainKey = mainKey;
    }

    public static void setTPDU(String TPDU) {
        My8583Ans.TPDU = TPDU;
    }

    public static void setMacKey(String macKey) {
        My8583Ans.macKey = macKey;
    }
}