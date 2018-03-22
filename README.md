# QRCodeApplication
QRCode

<!DOCTYPE html>
<html>
	<head>
		<meta charset="UTF-8">
		<title></title>
	</head>
	<body>
    

本demo是研究官方zxing demo 后，抽取其中主要的代码并进行简化，主要用于text文字的二维码生成和扫码，
其中使用了kotlin语音开发了activity的界面，其他都是java语言
＜/br＞  官方demo网址https://github.com/zxing/zxing
＜/br＞  扫码主要流程
（1）首先进入界面CaptureActivity，然后会初始化各种信息 
（2）核心方法是 initCamera(SurfaceHolder surfaceHolder)，用来初始化相机功能 
（3）initCamera方法中有一个类CaptureActivityHandler，这个类是用来控制扫码的预览、成功与失败回调、退出预览等主要功能 
（4）在CaptureActivityHandler这个类中，会start一个DecodeThread线程，这个线程是用来分析预览的数据是否有相关的二维码信息 
（5）DecodeThread中会初始化DecodeHandler，这个类用来发送分析数据的成功与失败 
（6）DecodeHandler中有一个核心方法decode(byte[] data, int width, int height)，这个方法用来分析预览的数据是否包含相关的二维码信息，如果有，就会分析里面的信息并生成一个Result类用来储存二维码的各类信息 
（7）最后小伙伴们就可以处理Result来显示需要的二维码信息，流程结束
    
	</body>
</html>

